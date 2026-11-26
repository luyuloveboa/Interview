package parallelsum;

import java.util.*;
import testutil.Check;
import parallelsum.base.ParallelSum;

public class ParallelSumTest {
    public static void main(String[] args) { run(); }

    public static void run() {
        base();
        randomVsDirect();
    }

    static void base() {
        Check.section("Parallel sum: base");
        sum("small [1,2,3,4,5] k=2 -> 15", new int[]{1, 2, 3, 4, 5}, 2, 15L);
        sum("negatives [-1,-2,3] k=3 -> 0", new int[]{-1, -2, 3}, 3, 0L);
        sum("empty [] k=4 -> 0", new int[]{}, 4, 0L);
        sum("single thread [7,8] k=1 -> 15", new int[]{7, 8}, 1, 15L);
        sum("overflow [2000000000, 2000000000] -> 4000000000", new int[]{2000000000, 2000000000}, 2, 4000000000L);
        sum("k > n [5,7] k=10 -> 12", new int[]{5, 7}, 10, 12L);
        sum("uneven split [1..10] k=3 -> 55", new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, 3, 55L);

        // Race-sensitive: a shared mutable total updated without synchronization loses
        // updates; returning before all futures complete gives a partial sum.
        Check.eq("large 1M ones x5 runs, exact every time", () -> {
            int[] ones = new int[1000000];
            Arrays.fill(ones, 1);
            for (int run = 0; run < 5; run++)
                if (new ParallelSum().parallelSum(ones, 8) != 1000000L) return "wrong sum on run " + run;
            return "ok";
        }, "ok");

        noLeakedPoolThreads();
    }

    static void sum(String name, int[] nums, int k, long expected) {
        Check.eq(name, () -> new ParallelSum().parallelSum(nums, k), expected);
    }

    /** A pool that is never shut down leaks non-daemon threads; the test run would hang. */
    static void noLeakedPoolThreads() {
        Check.eq("no leaked pool threads (forgot pool.shutdown()?)", () -> {
            Set<String> before = poolThreadNames();
            new ParallelSum().parallelSum(new int[]{1, 2, 3}, 2);
            long deadline = System.currentTimeMillis() + 1000;
            Set<String> leaked;
            do {
                leaked = poolThreadNames();
                leaked.removeAll(before);
                if (leaked.isEmpty()) return "ok";
                try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            } while (System.currentTimeMillis() < deadline);
            return "threads still alive 1s after return: " + leaked;
        }, "ok");
    }

    static Set<String> poolThreadNames() {
        Set<String> names = new HashSet<>();
        for (Thread t : Thread.getAllStackTraces().keySet())
            if (t.getName().startsWith("pool-")) names.add(t.getName());
        return names;
    }

    /** Random datasets vs a direct sequential sum. */
    static void randomVsDirect() {
        Check.section("Parallel sum: random datasets vs direct sum");
        Random rnd = new Random(1);
        int cases = 200;
        int mismatches = 0;
        String first = null;
        for (int it = 0; it < cases; it++) {
            int n = rnd.nextInt(10001);
            int k = 1 + rnd.nextInt(20);
            int[] a = randomArray(rnd, n);
            long expected = directSum(a);
            Check.Outcome o = Check.attempt(() -> new ParallelSum().parallelSum(a, k));
            if (o.matches(expected)) continue;
            if (mismatches++ == 0)
                first = "parallelSum(" + show(a) + ", k=" + k + ")\n  expected: " + expected + "\n  got:      " + o;
        }
        if (mismatches == 0) Check.eq("random datasets", 0, 0);
        else Check.fail("random datasets", mismatches + " of " + cases + " random datasets wrong. First one:\n" + first);
    }

    static long directSum(int[] a) {
        long s = 0;
        for (int v : a) s += v;
        return s;
    }

    static int[] randomArray(Random rnd, int n) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = rnd.nextInt(2_000_000_001) - 1_000_000_000;
        return a;
    }

    static String show(int[] a) {
        if (a.length <= 12) return Arrays.toString(a);
        return "[" + a[0] + ", " + a[1] + ", ... " + a.length + " values]";
    }
}
