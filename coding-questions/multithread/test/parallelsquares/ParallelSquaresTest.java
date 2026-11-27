package parallelsquares;

import java.util.*;
import testutil.Check;
import parallelsquares.base.ParallelSquares;

public class ParallelSquaresTest {
    public static void main(String[] args) { run(); }

    public static void run() {
        base();
        randomVsDirect();
    }

    static void base() {
        Check.section("Parallel squares: base");
        squares("small [1,2,3,4] k=2 -> [1,4,9,16]", new int[]{1, 2, 3, 4}, 2, new int[]{1, 4, 9, 16});
        squares("negatives [0,-3,5] k=1 -> [0,9,25]", new int[]{0, -3, 5}, 1, new int[]{0, 9, 25});
        squares("empty [] k=4 -> []", new int[]{}, 4, new int[]{});
        squares("k > n [5,7] k=10 -> [25,49]", new int[]{5, 7}, 10, new int[]{25, 49});
        squares("uneven split [1..10] k=3 -> squares", new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, 3,
                new int[]{1, 4, 9, 16, 25, 36, 49, 64, 81, 100});
        squares("boundary [10000,-10000] k=2 -> [100000000,100000000]", new int[]{10000, -10000}, 2,
                new int[]{100000000, 100000000});
        squares("single [7] k=3 -> [49]", new int[]{7}, 3, new int[]{49});
        squares("mixed signs [-1,0,1,-2,2] k=4 -> [1,0,1,4,4]", new int[]{-1, 0, 1, -2, 2}, 4,
                new int[]{1, 0, 1, 4, 4});

        // Returning before latch.await() hands back an unfilled array (zeros where the
        // workers have not written yet); countDown outside finally can hang await()
        // forever. Both bugs fail the checks above.
        noLeakedPoolThreads();
    }

    static void squares(String name, int[] nums, int k, int[] expected) {
        Check.eq(name, () -> new ParallelSquares().parallelSquares(nums, k), expected);
    }

    /** A pool that is never shut down leaks non-daemon threads; the test run would hang. */
    static void noLeakedPoolThreads() {
        Check.eq("no leaked pool threads (forgot pool.shutdown()?)", () -> {
            Set<String> before = poolThreadNames();
            new ParallelSquares().parallelSquares(new int[]{1, 2, 3}, 2);
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

    /** Random datasets vs a direct sequential computation. */
    static void randomVsDirect() {
        Check.section("Parallel squares: random datasets vs direct squares");
        Random rnd = new Random(2);
        int cases = 100;
        int mismatches = 0;
        String first = null;
        for (int it = 0; it < cases; it++) {
            int n = rnd.nextInt(51);
            int k = 1 + rnd.nextInt(4);
            int[] a = randomArray(rnd, n);
            int[] expected = directSquares(a);
            Check.Outcome o = Check.attempt(() -> new ParallelSquares().parallelSquares(a, k));
            if (o.matches(expected)) continue;
            if (mismatches++ == 0)
                first = "parallelSquares(" + show(a) + ", k=" + k + ")\n  expected: " + Check.show(expected) + "\n  got:      " + o;
        }
        if (mismatches == 0) Check.eq("random datasets", 0, 0);
        else Check.fail("random datasets", mismatches + " of " + cases + " random datasets wrong. First one:\n" + first);
    }

    static int[] directSquares(int[] a) {
        int[] r = new int[a.length];
        for (int i = 0; i < a.length; i++) r[i] = a[i] * a[i];
        return r;
    }

    static int[] randomArray(Random rnd, int n) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = rnd.nextInt(20001) - 10000;
        return a;
    }

    static String show(int[] a) {
        if (a.length <= 12) return Arrays.toString(a);
        return "[" + a[0] + ", " + a[1] + ", ... " + a.length + " values]";
    }
}
