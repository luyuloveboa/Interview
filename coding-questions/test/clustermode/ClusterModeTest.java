package clustermode;

import java.util.*;
import java.util.function.Supplier;
import testutil.Check;

public class ClusterModeTest {
    public static void main(String[] args) { run(); }

    public static void run() {
        base();
        fu1();
        randomVsDirect();
    }

    static void base() {
        Check.section("Cluster mode: base (findMode)");
        mode("ex", new int[]{1, 2, 2, 3, 3, 3, 4, 4, 4, 4}, 3, 4);
        mode("tie -> smallest value", new int[]{5, 5, -2, -2, 9}, 2, -2);
        mode("more workers than values", new int[]{8, 7}, 10, 7);
        mode("single value", new int[]{42}, 1, 42);
        mode("single worker", new int[]{3, 1, 3, 2}, 1, 3);
        mode("all values equal", new int[]{9, 9, 9, 9}, 4, 9);
        mode("all distinct -> smallest", new int[]{4, 2, 8, 6}, 3, 2);
        mode("extreme values", new int[]{1_000_000_000, -1_000_000_000, 1_000_000_000}, 2, 1_000_000_000);
        mode("negative mode", new int[]{-5, -5, -5, 0, 0}, 2, -5);
        mode("one value per worker, counts must be combined", new int[]{1, 2, 3, 1, 2, 3, 1}, 7, 1);
        mode("winner is spread over every worker", new int[]{7, 1, 7, 2, 7, 3, 7, 4}, 4, 7);
        int[] big = randomArray(new Random(3), 200_000, 5_000);
        mode("200k values on 200 workers", big, 200, directMode(big));
    }

    static void fu1() {
        Check.section("Cluster mode: FU1 (findMedian)");
        Check.eq("findMode still works: ex",
                () -> new clustermode.fu1.Cluster(new int[]{1, 2, 2, 3, 3, 3, 4, 4, 4, 4}, 3).findMode(), 4);
        median("ex", new int[]{1, 3, 5}, 2, 3.0);
        median("even count -> average of the two middles", new int[]{4, 1, 3, 2}, 3, 2.5);
        median("single value", new int[]{-3}, 1, -3.0);
        median("max values, no int overflow", new int[]{1_000_000_000, 1_000_000_000}, 2, 1.0E9);
        median("min and max", new int[]{-1_000_000_000, 1_000_000_000}, 2, 0.0);
        median("negative half-integer", new int[]{-1_000_000_000, -999_999_999}, 1, -9.999999995E8);
        median("more workers than values", new int[]{5, 1}, 10, 3.0);
        median("duplicates around the middle", new int[]{2, 2, 2, 7}, 2, 2.0);
        median("unsorted across workers", new int[]{9, 1, 8, 2, 7, 3, 6, 4, 5}, 3, 5.0);
        int[] big = randomArray(new Random(4), 200_000, 2_000_000_000);
        median("200k values on 200 workers", big, 200, directMedian(big));

        // network usage: ~31 binary-search rounds, a couple of tiny messages per worker per round
        int k = 10;
        clustermode.fu1.Cluster c = new clustermode.fu1.Cluster(randomArray(new Random(5), 10_000, 2_000_000_000), k);
        Check.eq("median network usage", () -> {
            c.findMedian();
            long msgs = c.messagesSent();
            return msgs <= 2L * k * 33 ? "ok" : msgs + " messages (expected at most " + 2 * k * 33 + ")";
        }, "ok");
    }

    static void mode(String name, int[] data, int k, int expected) {
        Check.eq(name + ": findMode(" + show(data) + ", k=" + k + ")",
                () -> new clustermode.base.Cluster(data, k).findMode(), expected);
    }

    static void median(String name, int[] data, int k, double expected) {
        Check.eq(name + ": findMedian(" + show(data) + ", k=" + k + ")",
                () -> new clustermode.fu1.Cluster(data, k).findMedian(), expected);
    }

    /** Random datasets (sometimes many repeats, sometimes the full value range) vs direct computation. */
    static void randomVsDirect() {
        Check.section("Cluster mode: random datasets vs direct computation");
        Random rnd = new Random(1);
        int cases = 300;
        int[] mismatches = new int[3];
        String[] first = new String[3];
        for (int it = 0; it < cases; it++) {
            int n = 1 + rnd.nextInt(200), k = 1 + rnd.nextInt(20);
            int[] a = rnd.nextBoolean() ? randomArray(rnd, n, 1 + rnd.nextInt(50)) : randomArray(rnd, n, 2_000_000_000);
            compare(0, mismatches, first, "base findMode(" + show(a) + ", k=" + k + ")",
                    () -> new clustermode.base.Cluster(a, k).findMode(), directMode(a));
            compare(1, mismatches, first, "FU1 findMode(" + show(a) + ", k=" + k + ")",
                    () -> new clustermode.fu1.Cluster(a, k).findMode(), directMode(a));
            compare(2, mismatches, first, "FU1 findMedian(" + show(a) + ", k=" + k + ")",
                    () -> new clustermode.fu1.Cluster(a, k).findMedian(), directMedian(a));
        }
        String[] names = {"base: random findMode", "FU1: random findMode", "FU1: random findMedian"};
        for (int v = 0; v < 3; v++) {
            if (mismatches[v] == 0) Check.eq(names[v], 0, 0);
            else Check.fail(names[v], mismatches[v] + " of " + cases + " random datasets wrong. First one:\n" + first[v]);
        }
    }

    static void compare(int v, int[] mismatches, String[] first, String call, Supplier<?> got, Object expected) {
        Check.Outcome o = Check.attempt(got);
        if (o.matches(expected)) return;
        String detail = call + "\n  expected: " + Check.show(expected) + "\n  got:      " + o;
        if (o.timedOut) {
            Check.fail("random datasets", detail);
            throw Check.abort();
        }
        if (mismatches[v]++ == 0) first[v] = detail;
    }

    static int directMode(int[] a) {
        Map<Integer, Integer> count = new HashMap<>();
        for (int v : a) count.merge(v, 1, Integer::sum);
        int best = 0, bestCount = -1;
        for (Map.Entry<Integer, Integer> e : count.entrySet())
            if (e.getValue() > bestCount || (e.getValue() == bestCount && e.getKey() < best)) {
                best = e.getKey();
                bestCount = e.getValue();
            }
        return best;
    }

    static double directMedian(int[] a) {
        int[] s = a.clone();
        Arrays.sort(s);
        return ((long) s[(s.length - 1) / 2] + s[s.length / 2]) / 2.0;
    }

    /** n values; spread = how many distinct values around 0 (2e9 = the whole allowed range). */
    static int[] randomArray(Random rnd, int n, int spread) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = (int) (rnd.nextInt(spread) - spread / 2L);
        return a;
    }

    static String show(int[] a) {
        if (a.length <= 12) return Arrays.toString(a);
        return "[" + a[0] + ", " + a[1] + ", ... " + a.length + " values]";
    }
}
