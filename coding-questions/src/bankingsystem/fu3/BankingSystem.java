package bankingsystem.fu3;

import java.util.*;

public class BankingSystem {
    static final int CASHBACK_DELAY = 86_400_000;                    // 24 hours in ms

    static class Account {
        int balance;
        int outgoing;                                                // money transferred out or paid
        List<Payment> payments = new ArrayList<>();                  // [FU3 NEW] so a merge can re-point them
    }

    /** One successful pay() call. */
    static class Payment {
        String accountId;                                            // current owner (changes on merge)
        int cashback, due;
        boolean received;
    }

    private final Map<String, Account> accounts = new HashMap<>();
    private final Map<String, Payment> payments = new HashMap<>();   // paymentId -> payment
    private final Deque<Payment> pending = new ArrayDeque<>();       // due = timestamp + const, so FIFO = sorted by due
    // [FU3 NEW] accountId -> (time -> balance after all changes at that time); -1 = account gone.
    // Kept by ID (not inside Account) so it survives the account being merged away.
    private final Map<String, TreeMap<Integer, Integer>> history = new HashMap<>();

    public BankingSystem() {}

    /** Called first by every operation: credit all cashback due at or before now. */
    private void processCashbacks(int timestamp) {
        while (!pending.isEmpty() && pending.peekFirst().due <= timestamp) {
            Payment p = pending.pollFirst();
            add(p.due, p.accountId, p.cashback);                     // [FU3 CHANGED] recorded at its due time
            p.received = true;
        }
    }

    public boolean createAccount(int timestamp, String accountId) {
        processCashbacks(timestamp);
        if (accounts.containsKey(accountId)) return false;
        accounts.put(accountId, new Account());
        add(timestamp, accountId, 0);                                // [FU3 NEW] record that it exists
        return true;
    }

    public int deposit(int timestamp, String accountId, int amount) {
        processCashbacks(timestamp);
        if (!accounts.containsKey(accountId)) return -1;
        return add(timestamp, accountId, amount);                    // [FU3 CHANGED] + timestamp
    }

    public int transfer(int timestamp, String sourceAccountId, String targetAccountId, int amount) {
        processCashbacks(timestamp);
        Account src = accounts.get(sourceAccountId), dst = accounts.get(targetAccountId);
        if (src == null || dst == null || sourceAccountId.equals(targetAccountId) || src.balance < amount) return -1;
        src.outgoing += amount;
        add(timestamp, targetAccountId, amount);                     // [FU3 CHANGED] + timestamp
        return add(timestamp, sourceAccountId, -amount);             // [FU3 CHANGED] + timestamp
    }

    /** Outgoing total descending, then account ID ascending. */
    public List<String> topSpenders(int timestamp, int n) {
        processCashbacks(timestamp);
        List<String> ids = new ArrayList<>(accounts.keySet());
        ids.sort((x, y) -> {
            int byOut = Integer.compare(accounts.get(y).outgoing, accounts.get(x).outgoing);
            return byOut != 0 ? byOut : x.compareTo(y);
        });
        List<String> res = new ArrayList<>();
        for (String id : ids.subList(0, Math.min(n, ids.size()))) res.add(id + "(" + accounts.get(id).outgoing + ")");
        return res;
    }

    /** Withdraw now (counts as outgoing), schedule 2% cashback (rounded down) 24h later. */
    public String pay(int timestamp, String accountId, int amount) {
        processCashbacks(timestamp);
        Account a = accounts.get(accountId);
        if (a == null || a.balance < amount) return "";
        a.outgoing += amount;
        add(timestamp, accountId, -amount);                          // [FU3 CHANGED] + timestamp

        Payment p = new Payment();
        p.accountId = accountId;
        p.cashback = amount / 50;                                    // 2% rounded down; amount * 2 could overflow
        p.due = timestamp + CASHBACK_DELAY;
        String id = "payment" + (payments.size() + 1);
        payments.put(id, p);
        pending.addLast(p);
        a.payments.add(p);                                           // [FU3 NEW]
        return id;
    }

    /** "" unless the account exists and the payment belongs to it. */
    public String getPaymentStatus(int timestamp, String accountId, String paymentId) {
        processCashbacks(timestamp);
        Payment p = payments.get(paymentId);
        if (!accounts.containsKey(accountId) || p == null || !p.accountId.equals(accountId)) return "";
        return p.received ? "CASHBACK_RECEIVED" : "IN_PROGRESS";
    }

    /** [FU3 NEW] Move balance, outgoing total and payments (incl. pending cashback) of id2 into id1. */
    public boolean mergeAccounts(int timestamp, String accountId1, String accountId2) {
        processCashbacks(timestamp);
        if (accountId1.equals(accountId2) || !accounts.containsKey(accountId1) || !accounts.containsKey(accountId2)) return false;
        Account a = accounts.get(accountId1), b = accounts.remove(accountId2);
        a.outgoing += b.outgoing;
        for (Payment p : b.payments) p.accountId = accountId1;       // pending cashback now credits id1
        a.payments.addAll(b.payments);
        add(timestamp, accountId1, b.balance);
        history.get(accountId2).put(timestamp, -1);                   // gone from this moment on
        return true;
    }

    /** [FU3 NEW] Balance after all operations with timestamp <= timeAt; -1 if it didn't exist then. */
    public int getBalance(int timestamp, String accountId, int timeAt) {
        processCashbacks(timestamp);
        TreeMap<Integer, Integer> h = history.get(accountId);
        Map.Entry<Integer, Integer> e = h == null ? null : h.floorEntry(timeAt);
        return e == null ? -1 : e.getValue();
    }

    /** [FU3 CHANGED] The only place a balance changes; also records it in the history. */
    private int add(int timestamp, String accountId, int delta) {
        Account a = accounts.get(accountId);
        a.balance += delta;
        history.computeIfAbsent(accountId, k -> new TreeMap<>()).put(timestamp, a.balance);
        return a.balance;
    }
}
