package bankingsystem.fu2;

import java.util.*;

public class BankingSystem {
    static final int CASHBACK_DELAY = 86_400_000;                    // [FU2 NEW] 24 hours in ms

    static class Account {
        int balance;
        int outgoing;                                                // money transferred out or paid
    }

    /** [FU2 NEW] One successful pay() call. */
    static class Payment {
        String accountId;
        int cashback, due;
        boolean received;
    }

    private final Map<String, Account> accounts = new HashMap<>();
    private final Map<String, Payment> payments = new HashMap<>();   // [FU2 NEW] paymentId -> payment
    private final Deque<Payment> pending = new ArrayDeque<>();       // [FU2 NEW] due = timestamp + const, so FIFO = sorted by due

    public BankingSystem() {}

    /** [FU2 NEW] Called first by every operation: credit all cashback due at or before now. */
    private void processCashbacks(int timestamp) {
        while (!pending.isEmpty() && pending.peekFirst().due <= timestamp) {
            Payment p = pending.pollFirst();
            add(p.accountId, p.cashback);
            p.received = true;
        }
    }

    public boolean createAccount(int timestamp, String accountId) {
        processCashbacks(timestamp);                                 // [FU2 NEW]
        if (accounts.containsKey(accountId)) return false;
        accounts.put(accountId, new Account());
        return true;
    }

    public int deposit(int timestamp, String accountId, int amount) {
        processCashbacks(timestamp);                                 // [FU2 NEW]
        if (!accounts.containsKey(accountId)) return -1;
        return add(accountId, amount);
    }

    public int transfer(int timestamp, String sourceAccountId, String targetAccountId, int amount) {
        processCashbacks(timestamp);                                 // [FU2 NEW]
        Account src = accounts.get(sourceAccountId), dst = accounts.get(targetAccountId);
        if (src == null || dst == null || sourceAccountId.equals(targetAccountId) || src.balance < amount) return -1;
        src.outgoing += amount;
        add(targetAccountId, amount);
        return add(sourceAccountId, -amount);
    }

    /** Outgoing total descending, then account ID ascending. */
    public List<String> topSpenders(int timestamp, int n) {
        processCashbacks(timestamp);                                 // [FU2 NEW]
        List<String> ids = new ArrayList<>(accounts.keySet());
        ids.sort((x, y) -> {
            int byOut = Integer.compare(accounts.get(y).outgoing, accounts.get(x).outgoing);
            return byOut != 0 ? byOut : x.compareTo(y);
        });
        List<String> res = new ArrayList<>();
        for (String id : ids.subList(0, Math.min(n, ids.size()))) res.add(id + "(" + accounts.get(id).outgoing + ")");
        return res;
    }

    /** [FU2 NEW] Withdraw now (counts as outgoing), schedule 2% cashback (rounded down) 24h later. */
    public String pay(int timestamp, String accountId, int amount) {
        processCashbacks(timestamp);
        Account a = accounts.get(accountId);
        if (a == null || a.balance < amount) return "";
        a.outgoing += amount;
        add(accountId, -amount);

        Payment p = new Payment();
        p.accountId = accountId;
        p.cashback = amount / 50;                                    // 2% rounded down; amount * 2 could overflow
        p.due = timestamp + CASHBACK_DELAY;
        String id = "payment" + (payments.size() + 1);
        payments.put(id, p);
        pending.addLast(p);
        return id;
    }

    /** [FU2 NEW] "" unless the account exists and the payment belongs to it. */
    public String getPaymentStatus(int timestamp, String accountId, String paymentId) {
        processCashbacks(timestamp);
        Payment p = payments.get(paymentId);
        if (!accounts.containsKey(accountId) || p == null || !p.accountId.equals(accountId)) return "";
        return p.received ? "CASHBACK_RECEIVED" : "IN_PROGRESS";
    }

    /** The only place a balance changes. Returns the new balance. */
    private int add(String accountId, int delta) {
        Account a = accounts.get(accountId);
        a.balance += delta;
        return a.balance;
    }
}
