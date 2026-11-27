package bankingsystem.base;

import java.util.*;

public class BankingSystem {
    static class Account {
        int balance;
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
        add(targetAccountId, amount);
        return add(sourceAccountId, -amount);
    }

    /** The only place a balance changes. Returns the new balance. */
    private int add(String accountId, int delta) {
        Account a = accounts.get(accountId);
        a.balance += delta;
        return a.balance;
    }
}
