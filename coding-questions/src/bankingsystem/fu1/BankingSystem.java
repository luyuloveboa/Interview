package bankingsystem.fu1;

import java.util.*;

public class BankingSystem {
    static class Account {
        int balance;
        int outgoing;                                                // [FU1 NEW] money transferred out
    }

    private final Map<String, Account> accounts = new HashMap<>();

    public BankingSystem() {}

    public boolean createAccount(int timestamp, String accountId) {
        if (accounts.containsKey(accountId)) return false;
        accounts.put(accountId, new Account());
        return true;
    }

    public int deposit(int timestamp, String accountId, int amount) {
        if (!accounts.containsKey(accountId)) return -1;
        return add(accountId, amount);
    }

    public int transfer(int timestamp, String sourceAccountId, String targetAccountId, int amount) {
        Account src = accounts.get(sourceAccountId), dst = accounts.get(targetAccountId);
        if (src == null || dst == null || sourceAccountId.equals(targetAccountId) || src.balance < amount) return -1;
        src.outgoing += amount;                                      // [FU1 NEW]
        add(targetAccountId, amount);
        return add(sourceAccountId, -amount);
    }

    /** [FU1 NEW] Outgoing total descending, then account ID ascending. */
    public List<String> topSpenders(int timestamp, int n) {
        List<String> ids = new ArrayList<>(accounts.keySet());
        ids.sort((x, y) -> {
            int byOut = Integer.compare(accounts.get(y).outgoing, accounts.get(x).outgoing);
            return byOut != 0 ? byOut : x.compareTo(y);
        });
        List<String> res = new ArrayList<>();
        for (String id : ids.subList(0, Math.min(n, ids.size()))) res.add(id + "(" + accounts.get(id).outgoing + ")");
        return res;
    }

    /** The only place a balance changes. Returns the new balance. */
    private int add(String accountId, int delta) {
        Account a = accounts.get(accountId);
        a.balance += delta;
        return a.balance;
    }
}
