package tasktree;

import java.util.*;
import testutil.Check;
import tasktree.base.TaskTree;

public class TaskTreeTest {
    public static void main(String[] args) { run(); }

    public static void run() {
        base();
        randomVsDirect();
    }

    static void base() {
        Check.section("Dynamic task tree: base");
        traverse("chain 0->[1]->[2]->[3], start 0 -> {0,1,2,3}",
                graph(new int[]{0, 1}, new int[]{1, 2}, new int[]{2, 3}), 0, 2,
                Set.of(0, 1, 2, 3));
        traverse("diamond 0->[1,2], 1->[3], 2->[3], start 0 -> {0,1,2,3}, 4 processed",
                graph(new int[]{0, 1, 2}, new int[]{1, 3}, new int[]{2, 3}), 0, 4,
                Set.of(0, 1, 2, 3));
        traverse("cycle 0->[1], 1->[0,2], start 0 -> {0,1,2} and terminates",
                graph(new int[]{0, 1}, new int[]{1, 0, 2}), 0, 2,
                Set.of(0, 1, 2));
        traverse("start not a key, empty graph, start 9 -> {9}",
                new HashMap<>(), 9, 2, Set.of(9));
        traverse("start not a key, non-empty graph, start 9 -> {9}",
                graph(new int[]{5, 6}), 9, 2, Set.of(9));
        traverse("self-loop 0->[0], start 0 -> {0}, 1 processed",
                graph(new int[]{0, 0}), 0, 2, Set.of(0));
        traverse("duplicate edges 0->[1,1,1], 1->[2,2], start 0 -> {0,1,2}, 3 processed",
                graph(new int[]{0, 1, 1, 1}, new int[]{1, 2, 2}), 0, 4,
                Set.of(0, 1, 2));
        traverse("disconnected nodes not reached: 0->[1], 5->[6], 7->[]; start 0 -> {0,1}",
                graph(new int[]{0, 1}, new int[]{5, 6}, new int[]{7}), 0, 2,
                Set.of(0, 1));
        traverse("single thread k=1, diamond -> {0,1,2,3}",
                graph(new int[]{0, 1, 2}, new int[]{1, 3}, new int[]{2, 3}), 0, 1,
                Set.of(0, 1, 2, 3));
        traverse("k > n: chain of 3, k=16 -> {0,1,2}",
                graph(new int[]{0, 1}, new int[]{1, 2}), 0, 16,
                Set.of(0, 1, 2));
        traverse("wide fan-out 0->[1..20], start 0 -> 21 nodes, 21 processed",
                fanOut(20), 0, 8, fanOutSet(20));

        // processedCount must reset at the start of each traverse on the same instance.
        Check.eq("same instance reused: diamond then chain of 3, second count is 3",
                () -> {
                    TaskTree tree = new TaskTree();
                    tree.traverse(graph(new int[]{0, 1, 2}, new int[]{1, 3}, new int[]{2, 3}), 0, 4);
                    Set<Integer> second = tree.traverse(graph(new int[]{0, 1}, new int[]{1, 2}), 0, 2);
                    return List.of(new TreeSet<>(second), tree.processedCount);
                },
                List.of(new TreeSet<>(Set.of(0, 1, 2)), 3));

        // Register-after-submit lets the phase advance early (incomplete set); a missing
        // arriveAndDeregister in finally hangs the main thread forever. Both fail here.
        noLeakedPoolThreads();
    }

    /** Runs traverse and checks BOTH the reachable set and processedCount. */
    static void traverse(String name, Map<Integer, List<Integer>> graph, int start, int k,
                         Set<Integer> expected) {
        Check.eq(name, () -> {
            TaskTree tree = new TaskTree();
            Set<Integer> result = tree.traverse(graph, start, k);
            return List.of(new TreeSet<>(result), tree.processedCount);
        }, List.of(new TreeSet<>(expected), expected.size()));
    }

    /** Builds a graph from rows: each row is {from, to, to, ...}. A node with no row is a leaf. */
    static Map<Integer, List<Integer>> graph(int[]... rows) {
        Map<Integer, List<Integer>> g = new HashMap<>();
        for (int[] row : rows) {
            List<Integer> tos = new ArrayList<>();
            for (int i = 1; i < row.length; i++) tos.add(row[i]);
            g.put(row[0], tos);
        }
        return g;
    }

    static Map<Integer, List<Integer>> fanOut(int leaves) {
        List<Integer> tos = new ArrayList<>();
        for (int i = 1; i <= leaves; i++) tos.add(i);
        return Map.of(0, tos);
    }

    static Set<Integer> fanOutSet(int leaves) {
        Set<Integer> s = new HashSet<>();
        for (int i = 0; i <= leaves; i++) s.add(i);
        return s;
    }

    /** A pool that is never shut down leaks non-daemon threads; the test run would hang. */
    static void noLeakedPoolThreads() {
        Check.eq("no leaked pool threads (forgot pool.shutdown()?)", () -> {
            Set<String> before = poolThreadNames();
            new TaskTree().traverse(graph(new int[]{0, 1}, new int[]{1, 2}), 0, 2);
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

    /** Random graphs (cycles, duplicate edges, self-loops, disconnected parts) vs sequential BFS. */
    static void randomVsDirect() {
        Check.section("Dynamic task tree: random graphs vs sequential BFS");
        Random rnd = new Random(9);
        int cases = 100;
        int mismatches = 0;
        String first = null;
        for (int it = 0; it < cases; it++) {
            int n = rnd.nextInt(61);
            int k = 1 + rnd.nextInt(16);
            Map<Integer, List<Integer>> g = randomGraph(rnd, n);
            int start = n == 0 ? 0 : rnd.nextInt(n);
            Set<Integer> expected = directBfs(g, start);
            Check.Outcome o = Check.attempt(() -> {
                TaskTree tree = new TaskTree();
                Set<Integer> result = tree.traverse(g, start, k);
                return List.of(new TreeSet<>(result), tree.processedCount);
            });
            Object want = List.of(new TreeSet<>(expected), expected.size());
            if (o.matches(want)) continue;
            if (mismatches++ == 0)
                first = "traverse(" + show(g) + ", start=" + start + ", k=" + k + ")\n  expected: " + want + "\n  got:      " + o;
        }
        if (mismatches == 0) Check.eq("random graphs", 0, 0);
        else Check.fail("random graphs", mismatches + " of " + cases + " random graphs wrong. First one:\n" + first);
    }

    static Map<Integer, List<Integer>> randomGraph(Random rnd, int n) {
        Map<Integer, List<Integer>> g = new HashMap<>();
        for (int node = 0; node < n; node++) {
            int out = rnd.nextInt(4);                          // 0..3 out-edges
            if (out == 0 && rnd.nextBoolean()) continue;      // node absent from map: a leaf
            List<Integer> tos = new ArrayList<>();
            for (int i = 0; i < out; i++) tos.add(rnd.nextInt(n));  // self-loops and duplicates allowed
            g.put(node, tos);
        }
        return g;
    }

    static Set<Integer> directBfs(Map<Integer, List<Integer>> graph, int start) {
        Set<Integer> seen = new HashSet<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            int node = queue.remove();
            for (int next : graph.getOrDefault(node, List.of())) {
                if (seen.add(next)) queue.add(next);
            }
        }
        return seen;
    }

    static String show(Map<Integer, List<Integer>> g) {
        if (g.size() <= 12) return String.valueOf(g);
        return "{... " + g.size() + " keys ...}";
    }
}
