package gpucredit.base;

import java.util.*;

public class CreditSystem {
    List<int[]> grants = new ArrayList<>();             // {amount, startTime, expirationTime}
    TreeMap<Integer, Integer> subs = new TreeMap<>();   // timestamp -> amount

    public CreditSystem() {}

    public void grantCredit(String id, int amount, int startTime, int expirationTime) {
        grants.add(new int[]{amount, startTime, expirationTime});
    }

    public void subtract(int amount, int timestamp) {
        subs.merge(timestamp, amount, Integer::sum);
    }

    public int getBalance(int timestamp) {
        int total = 0;
        for (int[] g : grants) if (active(g, timestamp)) total += g[0];
        total -= subs.getOrDefault(timestamp, 0);       // only affects this exact timestamp
        return total < 0 ? -1 : total;
    }

    boolean active(int[] g, int t) { return g[1] <= t && t < g[2]; }
}
