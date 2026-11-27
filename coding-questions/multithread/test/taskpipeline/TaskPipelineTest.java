package taskpipeline;

import java.util.*;
import testutil.Check;
import taskpipeline.base.TaskPipeline;

public class TaskPipelineTest {
    public static void main(String[] args) { run(); }

    public static void run() {
        base();
        randomVsDirect();
    }

    static void base() {
        Check.section("Task pipeline: base");
        pipeline("small [1,2,3] cap 10 consumers 2 -> [2,4,6]",
                List.of(1, 2, 3), 10, 2, List.of(2, 4, 6));
        pipeline("empty [] cap 5 consumers 2 -> []",
                List.of(), 5, 2, List.of());
        pipeline("single [5] cap 1 consumer 1 -> [10]",
                List.of(5), 1, 1, List.of(10));
        // Capacity 1 forces the producer to block on put() until a consumer drains
        // the queue; a solution that never takes (or takes only once) hangs here.
        pipeline("capacity 1 [1..10] cap 1 consumers 3 -> doubled",
                List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), 1, 3,
                List.of(2, 4, 6, 8, 10, 12, 14, 16, 18, 20));
        // More consumers than inputs: the extra consumers must still get a pill
        // and exit, or the method never returns.
        pipeline("consumers > inputs [4,9] cap 2 consumers 8 -> [8,18]",
                List.of(4, 9), 2, 8, List.of(8, 18));
        pipeline("larger input [1..1000] cap 7 consumers 4 -> doubled",
                range(1, 1000), 7, 4, doubled(range(1, 1000)));
        // Duplicates: the comparison is a multiset comparison, so dropping or
        // double-counting a repeated value fails.
        pipeline("duplicates [3,3,3] cap 2 consumers 2 -> [6,6,6]",
                List.of(3, 3, 3), 2, 2, List.of(6, 6, 6));
        pipeline("negatives and zero [-5,0,7] cap 3 consumers 2 -> [-10,0,14]",
                List.of(-5, 0, 7), 3, 2, List.of(-10, 0, 14));

        // Forgetting the poison pill (or sending only one for N consumers) leaves
        // consumers blocked on take() forever; the harness timeout aborts the check.
        noLeakedPoolThreads();
    }

    /** Results are order-free, so both sides are compared sorted (as multisets). */
    static void pipeline(String name, List<Integer> inputs, int cap, int consumers, List<Integer> expected) {
        Check.eq(name, () -> sorted(new TaskPipeline().runPipeline(inputs, cap, consumers)), sorted(expected));
    }

    static List<Integer> sorted(List<Integer> xs) {
        List<Integer> copy = new ArrayList<>(xs);
        Collections.sort(copy);
        return copy;
    }

    static List<Integer> range(int from, int to) {
        List<Integer> xs = new ArrayList<>();
        for (int i = from; i <= to; i++) xs.add(i);
        return xs;
    }

    static List<Integer> doubled(List<Integer> xs) {
        List<Integer> ys = new ArrayList<>();
        for (int x : xs) ys.add(x * 2);
        return ys;
    }

    /** A pool that is never shut down leaks non-daemon threads; the test run would hang. */
    static void noLeakedPoolThreads() {
        Check.eq("no leaked pool threads (forgot pool.shutdown()?)", () -> {
            Set<String> before = poolThreadNames();
            new TaskPipeline().runPipeline(List.of(1, 2, 3), 2, 2);
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

    /** Random datasets vs a direct sequential computation (map task * 2). */
    static void randomVsDirect() {
        Check.section("Task pipeline: random datasets vs direct doubling");
        Random rnd = new Random(4);
        int cases = 100;
        int mismatches = 0;
        String first = null;
        for (int it = 0; it < cases; it++) {
            int n = rnd.nextInt(201);
            int cap = 1 + rnd.nextInt(10);
            int consumers = 1 + rnd.nextInt(8);
            List<Integer> inputs = randomList(rnd, n);
            List<Integer> expected = sorted(doubled(inputs));
            Check.Outcome o = Check.attempt(() -> sorted(new TaskPipeline().runPipeline(inputs, cap, consumers)));
            if (o.matches(expected)) continue;
            if (mismatches++ == 0)
                first = "runPipeline(" + show(inputs) + ", cap=" + cap + ", consumers=" + consumers + ")\n  expected: " + expected + "\n  got:      " + o;
        }
        if (mismatches == 0) Check.eq("random datasets", 0, 0);
        else Check.fail("random datasets", mismatches + " of " + cases + " random datasets wrong. First one:\n" + first);
    }

    static List<Integer> randomList(Random rnd, int n) {
        List<Integer> xs = new ArrayList<>();
        for (int i = 0; i < n; i++) xs.add(rnd.nextInt(2001) - 1000);
        return xs;
    }

    static String show(List<Integer> xs) {
        if (xs.size() <= 12) return xs.toString();
        return "[" + xs.get(0) + ", " + xs.get(1) + ", ... " + xs.size() + " values]";
    }
}
