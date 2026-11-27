package uniqueurl;

import java.util.*;
import testutil.Check;
import uniqueurl.base.UniqueUrlProcessor;

public class UniqueUrlProcessorTest {
    public static void main(String[] args) { run(); }

    public static void run() {
        base();
        randomVsDirect();
    }

    static void base() {
        Check.section("Unique URL processor: base");
        unique("spec [a,b,a,c,b] k=2 -> [a,b,c]",
                List.of("a", "b", "a", "c", "b"), 2, List.of("a", "b", "c"));
        unique("spec empty [] k=4 -> []", List.of(), 4, List.of());
        unique("spec all same [x,x,x] k=2 -> [x]",
                List.of("x", "x", "x"), 2, List.of("x"));
        unique("all duplicates [u,u,u,u,u] k=4 -> [u]",
                List.of("u", "u", "u", "u", "u"), 4, List.of("u"));
        unique("single [only] k=1 -> [only]", List.of("only"), 1, List.of("only"));
        unique("k > n [a,b] k=16 -> [a,b]", List.of("a", "b"), 16, List.of("a", "b"));
        unique("interleaved [a,b,c,a,b,c,a] k=3 -> [a,b,c]",
                List.of("a", "b", "c", "a", "b", "c", "a"), 3, List.of("a", "b", "c"));
        unique("k=1 with duplicates [a,a,b,a,b,c] k=1 -> [a,b,c]",
                List.of("a", "a", "b", "a", "b", "c"), 1, List.of("a", "b", "c"));

        // Race-sensitive: the two-step contains-then-add lets two threads claim the
        // same URL, so the result contains duplicates. The leading run of identical
        // URLs makes all pool threads contend on the same claim at once; 200 URLs
        // from 5 distinct values, run 50 times — a correct atomic claim never
        // duplicates, the two-step bug shows up quickly.
        Check.eq("race: 200 urls, 5 distinct, k=8, x50 runs, no duplicates ever", () -> {
            List<String> input = new ArrayList<>();
            for (int i = 0; i < 16; i++) input.add("url0");
            for (int i = 16; i < 200; i++) input.add("url" + (i % 5));
            Set<String> expectedSet = Set.of("url0", "url1", "url2", "url3", "url4");
            for (int run = 0; run < 50; run++) {
                List<String> got = new UniqueUrlProcessor().processUnique(input, 8);
                if (got.size() != new HashSet<>(got).size())
                    return "run " + run + " returned duplicates: " + got.size() + " entries, "
                            + new HashSet<>(got).size() + " distinct";
                if (!new HashSet<>(got).equals(expectedSet))
                    return "run " + run + " wrong set: " + got;
            }
            return "ok";
        }, "ok");

        noLeakedPoolThreads();
    }

    /** Order is free, so both sides are sorted before comparing. */
    static void unique(String name, List<String> urls, int k, List<String> expected) {
        Check.eq(name, () -> sorted(new UniqueUrlProcessor().processUnique(urls, k)), sorted(expected));
    }

    static List<String> sorted(List<String> urls) {
        List<String> copy = new ArrayList<>(urls);
        Collections.sort(copy);
        return copy;
    }

    /** A pool that is never shut down leaks non-daemon threads; the test run would hang. */
    static void noLeakedPoolThreads() {
        Check.eq("no leaked pool threads (forgot pool.shutdown()?)", () -> {
            Set<String> before = poolThreadNames();
            new UniqueUrlProcessor().processUnique(List.of("a", "b", "c"), 2);
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

    /** Random datasets vs a direct sequential computation (HashSet distinct). */
    static void randomVsDirect() {
        Check.section("Unique URL processor: random datasets vs direct distinct set");
        Random rnd = new Random(3);
        int cases = 100;
        int mismatches = 0;
        String first = null;
        for (int it = 0; it < cases; it++) {
            int n = rnd.nextInt(41);
            int k = 1 + rnd.nextInt(8);
            List<String> urls = randomUrls(rnd, n);
            List<String> expected = directUnique(urls);
            Check.Outcome o = Check.attempt(() -> sorted(new UniqueUrlProcessor().processUnique(urls, k)));
            if (o.matches(expected)) continue;
            if (mismatches++ == 0)
                first = "processUnique(" + show(urls) + ", k=" + k + ")\n  expected: " + expected + "\n  got:      " + o;
        }
        if (mismatches == 0) Check.eq("random datasets", 0, 0);
        else Check.fail("random datasets", mismatches + " of " + cases + " random datasets wrong. First one:\n" + first);
    }

    /** Direct sequential reference: distinct URLs, sorted (order is free). */
    static List<String> directUnique(List<String> urls) {
        List<String> distinct = new ArrayList<>(new HashSet<>(urls));
        Collections.sort(distinct);
        return distinct;
    }

    /** Small alphabet (10 values) so duplicates are frequent. */
    static List<String> randomUrls(Random rnd, int n) {
        List<String> urls = new ArrayList<>();
        for (int i = 0; i < n; i++) urls.add("url" + rnd.nextInt(10));
        return urls;
    }

    static String show(List<String> urls) {
        if (urls.size() <= 12) return urls.toString();
        return "[" + urls.get(0) + ", " + urls.get(1) + ", ... " + urls.size() + " values]";
    }
}
