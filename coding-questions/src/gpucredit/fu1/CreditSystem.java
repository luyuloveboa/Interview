package gpucredit.fu1;

import java.util.*;

public class CreditSystem {
    List<int[]> grants = new ArrayList<>();             // {amount, startTime, expirationTime}
    TreeMap<Integer, Integer> subs = new TreeMap<>();   // timestamp -> amount

    public CreditSystem() {}

    public void grantCredit(String id, int amount, int startTime, int expirationTime) {
        grants.add(new int[]{amount, startTime, expirationTime});
        grants.sort((a, b) -> a[2] - b[2]);             // [FU1 NEW] keep sorted by expiration (soonest first)
    }

    public void subtract(int amount, int timestamp) {
        subs.merge(timestamp, amount, Integer::sum);
    }

    public int getBalance(int timestamp) {
        // [FU1 NEW] replay all subtracts at t <= timestamp in time order; each eats soonest-expiring active grants first
        int[] left = new int[grants.size()];
        for (int i = 0; i < left.length; i++) left[i] = grants.get(i)[0];
        for (Map.Entry<Integer, Integer> e : subs.headMap(timestamp, true).entrySet()) {
            int t = e.getKey(), need = e.getValue();
            for (int i = 0; i < left.length && need > 0; i++) {
                if (!active(grants.get(i), t)) continue;
                int take = Math.min(need, left[i]);
                left[i] -= take;
                need -= take;
            }
            if (need > 0) return -1;                    // [FU1 NEW] some earlier usage can't be covered
        }

        int total = 0;
        for (int i = 0; i < left.length; i++)
            if (active(grants.get(i), timestamp)) total += left[i];   // [FU1 CHANGED] remaining, not original amount
        return total;                                   // [FU1 CHANGED] -1 is now decided during replay
    }

    boolean active(int[] g, int t) { return g[1] <= t && t < g[2]; }
}
