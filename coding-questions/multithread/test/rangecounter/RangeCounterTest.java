package rangecounter;

import java.util.*;
import testutil.Check;
import rangecounter.base.RangeCounter;

public class RangeCounterTest {
    public static void main(String[] args) { run(); }

    public static void run() {
        base();
        randomVsDirect();
    }

    static void base() {
        Check.section("Range counter: base");
        count("example [1,5,3,8,2] low 2 high 5 -> 3", new int[]{1, 5, 3, 8, 2}, 2, 5, 3L);
        count("empty [] low 0 high 10 -> 0", new int[]{}, 0, 10, 0L);
        count("all equal [7,7,7] low 7 high 7 -> 3", new int[]{7, 7, 7}, 7, 7, 3L);
        count("none in range [1,2,3] low 5 high 10 -> 0", new int[]{1, 2, 3}, 5, 10, 0L);
        count("single in range [5] low 5 high 5 -> 1", new int[]{5}, 5, 5, 1L);
        count("single out of range [5] low 6 high 10 -> 0", new int[]{5}, 6, 10, 0L);
        count("low == high [3,7,7,7,9] low 7 high 7 -> 3", new int[]{3, 7, 7, 7, 9}, 7, 7, 3L);
        count("all in range [1..10] low 0 high 100 -> 10",
                new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, 0, 100, 10L);
        count("none in range, high values [1,2,3] low 100 high 200 -> 0", new int[]{1, 2, 3}, 100, 200, 0L);
        count("negatives [-5,-1,0,3,7] low -5 high 0 -> 3", new int[]{-5, -1, 0, 3, 7}, -5, 0, 3L);
        count("int extremes [MIN_VALUE, MAX_VALUE] low MIN high MAX -> 2",
                new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE}, Integer.MIN_VALUE, Integer.MAX_VALUE, 2L);
        count("boundary values included [2,5] low 2 high 5 -> 2", new int[]{1, 2, 5, 6}, 2, 5, 2L);

        // Fork-without-join loses half the count and a shared unsynchronized counter
        // loses updates; both fail here because the array is far above the threshold.
        Check.eq("large 1M cycling 0..9, low 3 high 6 -> 400000, x3 runs exact", () -> {
            int[] cycling = new int[1000000];
            for (int i = 0; i < cycling.length; i++) cycling[i] = i % 10;
            for (int run = 0; run < 3; run++)
                if (new RangeCounter().countInRange(cycling, 3, 6) != 400000L)
                    return "wrong count on run " + run;
            return "ok";
        }, "ok");

        noLeakedPoolThreads();
    }

    static void count(String name, int[] nums, int low, int high, long expected) {
        Check.eq(name, () -> new RangeCounter().countInRange(nums, low, high), expected);
    }

    /** A pool that is never shut down leaks non-daemon threads; the test run would hang. */
    static void noLeakedPoolThreads() {
        Check.eq("no leaked pool threads (forgot pool.shutdown()?)", () -> {
            Set<String> before = poolThreadNames();
            new RangeCounter().countInRange(new int[]{1, 2, 3}, 0, 10);
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
            if (t.getName().startsWith("ForkJoinPool-")) names.add(t.getName());
        return names;
    }

    /** Random datasets vs a direct sequential count. */
    static void randomVsDirect() {
        Check.section("Range counter: random datasets vs direct count");
        Random rnd = new Random(8);
        int cases = 100;
        int mismatches = 0;
        String first = null;
        for (int it = 0; it < cases; it++) {
            int n = rnd.nextInt(200001);
            int[] a = randomArray(rnd, n);
            int endpointA = rnd.nextInt(2_000_000_001) - 1_000_000_000;
            int endpointB = rnd.nextInt(2_000_000_001) - 1_000_000_000;
            int low = Math.min(endpointA, endpointB);
            int high = Math.max(endpointA, endpointB);
            long expected = directCount(a, low, high);
            Check.Outcome o = Check.attempt(() -> new RangeCounter().countInRange(a, low, high));
            if (o.matches(expected)) continue;
            if (mismatches++ == 0)
                first = "countInRange(" + show(a) + ", low=" + low + ", high=" + high + ")\n  expected: " + expected + "\n  got:      " + o;
        }
        if (mismatches == 0) Check.eq("random datasets", 0, 0);
        else Check.fail("random datasets", mismatches + " of " + cases + " random datasets wrong. First one:\n" + first);
    }

    static long directCount(int[] a, int low, int high) {
        long c = 0;
        for (int v : a) if (v >= low && v <= high) c++;
        return c;
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
