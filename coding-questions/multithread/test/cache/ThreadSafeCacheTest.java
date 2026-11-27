package cache;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import testutil.Check;
import cache.base.ThreadSafeCache;

public class ThreadSafeCacheTest {
    public static void main(String[] args) { run(); }

    public static void run() {
        base();
        concurrentStress();
        randomVsModel();
    }

    static void base() {
        Check.section("Thread-safe cache: base");
        Check.eq("put a=1, get a -> 1", () -> {
            ThreadSafeCache cache = new ThreadSafeCache();
            cache.put("a", "1");
            return cache.get("a");
        }, "1");
        Check.eq("get missing -> null", () -> new ThreadSafeCache().get("missing"), null);
        Check.eq("overwrite a=1 then a=2, get a -> 2", () -> {
            ThreadSafeCache cache = new ThreadSafeCache();
            cache.put("a", "1");
            cache.put("a", "2");
            return cache.get("a");
        }, "2");
        Check.eq("overwrite keeps size 1", () -> {
            ThreadSafeCache cache = new ThreadSafeCache();
            cache.put("a", "1");
            cache.put("a", "2");
            return cache.size();
        }, 1);
        Check.eq("size of empty cache -> 0", () -> new ThreadSafeCache().size(), 0);
        Check.eq("size after 5 distinct puts -> 5", () -> {
            ThreadSafeCache cache = new ThreadSafeCache();
            for (int i = 0; i < 5; i++) cache.put("k" + i, "v" + i);
            return cache.size();
        }, 5);
        Check.eq("100 distinct keys: size 100 and every get correct", () -> {
            ThreadSafeCache cache = new ThreadSafeCache();
            for (int i = 0; i < 100; i++) cache.put("k" + i, "v" + i);
            if (cache.size() != 100) return "size " + cache.size() + ", expected 100";
            for (int i = 0; i < 100; i++)
                if (!("v" + i).equals(cache.get("k" + i)))
                    return "get(k" + i + ") = " + cache.get("k" + i) + ", expected v" + i;
            return "ok";
        }, "ok");
        Check.eq("empty-string key and value", () -> {
            ThreadSafeCache cache = new ThreadSafeCache();
            cache.put("", "");
            if (!"".equals(cache.get(""))) return "get(\"\") = " + cache.get("");
            if (cache.size() != 1) return "size " + cache.size() + ", expected 1";
            return "ok";
        }, "ok");
    }

    static void concurrentStress() {
        Check.section("Thread-safe cache: concurrent stress");
        Check.eq("16 threads x 1000 distinct keys, x3 runs: size 16000 and every get correct", () -> {
            for (int rep = 0; rep < 3; rep++) {
                ThreadSafeCache cache = new ThreadSafeCache();
                String bad = distinctPuts(cache, 16, 1000);
                if (bad != null) return "rep " + rep + ": " + bad;
            }
            return "ok";
        }, "ok");
        Check.eq("concurrent readers during writes never see a wrong value", () -> {
            ThreadSafeCache cache = new ThreadSafeCache();
            int writers = 4, keysPerWriter = 1000;
            int totalKeys = writers * keysPerWriter;
            AtomicBoolean done = new AtomicBoolean(false);
            AtomicReference<String> violation = new AtomicReference<>(null);
            AtomicReference<Throwable> error = new AtomicReference<>(null);
            List<Thread> threads = new ArrayList<>();
            for (int w = 0; w < writers; w++) {
                int writer = w;
                threads.add(new Thread(() -> {
                    try {
                        for (int i = 0; i < keysPerWriter; i++)
                            cache.put("w" + writer + "-k" + i, "v" + writer + "-" + i);
                    } catch (Throwable t) { error.compareAndSet(null, t); }
                }));
            }
            Random rnd = new Random(7);
            for (int r = 0; r < 4; r++) {
                threads.add(new Thread(() -> {
                    try {
                        while (!done.get()) {
                            int writer = rnd.nextInt(writers);
                            int key = rnd.nextInt(keysPerWriter);
                            // Each key is written exactly once with one fixed value,
                            // so a read must see null (not written yet) or that value.
                            String got = cache.get("w" + writer + "-k" + key);
                            String expected = "v" + writer + "-" + key;
                            if (got != null && !got.equals(expected))
                                violation.compareAndSet(null, "get(w" + writer + "-k" + key
                                        + ") = " + got + ", expected null or " + expected);
                        }
                    } catch (Throwable t) { error.compareAndSet(null, t); }
                }));
            }
            for (Thread t : threads) t.start();
            for (int i = 0; i < writers; i++) {
                try { threads.get(i).join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            done.set(true);
            for (int i = writers; i < threads.size(); i++) {
                try { threads.get(i).join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            if (error.get() != null) return "thread threw " + error.get();
            if (violation.get() != null) return violation.get();
            if (cache.size() != totalKeys) return "size " + cache.size() + ", expected " + totalKeys;
            for (int w = 0; w < writers; w++)
                for (int i = 0; i < keysPerWriter; i++)
                    if (!("v" + w + "-" + i).equals(cache.get("w" + w + "-k" + i)))
                        return "get(w" + w + "-k" + i + ") wrong after writers finished";
            return "ok";
        }, "ok");
        Check.eq("16 threads overwriting one key: final value is a written value, size 1", () -> {
            ThreadSafeCache cache = new ThreadSafeCache();
            Set<String> written = ConcurrentHashMap.newKeySet();
            AtomicReference<Throwable> error = new AtomicReference<>(null);
            List<Thread> threads = new ArrayList<>();
            for (int w = 0; w < 16; w++) {
                int writer = w;
                threads.add(new Thread(() -> {
                    try {
                        for (int i = 0; i < 1000; i++) {
                            String value = "w" + writer + "-" + i;
                            written.add(value);
                            cache.put("shared", value);
                        }
                    } catch (Throwable t) { error.compareAndSet(null, t); }
                }));
            }
            for (Thread t : threads) t.start();
            for (Thread t : threads) {
                try { t.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            if (error.get() != null) return "thread threw " + error.get();
            if (cache.size() != 1) return "size " + cache.size() + ", expected 1";
            String got = cache.get("shared");
            if (got == null || !written.contains(got)) return "get(shared) = " + got + ", not a written value";
            return "ok";
        }, "ok");
    }

    /** Each of {@code threadCount} threads puts {@code keysPerThread} distinct keys. */
    static String distinctPuts(ThreadSafeCache cache, int threadCount, int keysPerThread) {
        AtomicReference<Throwable> error = new AtomicReference<>(null);
        List<Thread> threads = new ArrayList<>();
        for (int w = 0; w < threadCount; w++) {
            int writer = w;
            threads.add(new Thread(() -> {
                try {
                    for (int i = 0; i < keysPerThread; i++)
                        cache.put("t" + writer + "-k" + i, "v" + writer + "-" + i);
                } catch (Throwable t) { error.compareAndSet(null, t); }
            }));
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) {
            try { t.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        if (error.get() != null) return "thread threw " + error.get();
        int expected = threadCount * keysPerThread;
        if (cache.size() != expected) return "size " + cache.size() + ", expected " + expected;
        for (int w = 0; w < threadCount; w++)
            for (int i = 0; i < keysPerThread; i++)
                if (!("v" + w + "-" + i).equals(cache.get("t" + w + "-k" + i)))
                    return "get(t" + w + "-k" + i + ") = " + cache.get("t" + w + "-k" + i)
                            + ", expected v" + w + "-" + i;
        return null;
    }

    /** Random put/get/size operations vs a single-threaded HashMap model. */
    static void randomVsModel() {
        Check.section("Thread-safe cache: random ops vs single-threaded model");
        Random rnd = new Random(5);
        Map<String, String> model = new HashMap<>();
        ThreadSafeCache cache = new ThreadSafeCache();
        int cases = 5000, mismatches = 0;
        String first = null;
        try {
            for (int it = 0; it < cases; it++) {
                int op = rnd.nextInt(100);
                String key = "k" + rnd.nextInt(20);
                if (op < 60) {                                    // put
                    String value = "v" + rnd.nextInt(100);
                    model.put(key, value);
                    cache.put(key, value);
                } else if (op < 85) {                             // get
                    String expected = model.get(key);
                    String got = cache.get(key);
                    if (!Objects.equals(got, expected)) {
                        if (mismatches++ == 0)
                            first = "get(" + key + ")\n  expected: " + expected + "\n  got:      " + got;
                    }
                } else {                                          // size
                    if (cache.size() != model.size()) {
                        if (mismatches++ == 0)
                            first = "size()\n  expected: " + model.size() + "\n  got:      " + cache.size();
                    }
                }
            }
        } catch (Throwable t) {
            Check.fail("random ops vs model", Check.describe(t));
            return;
        }
        if (mismatches == 0) Check.eq("random ops vs model", 0, 0);
        else Check.fail("random ops vs model", mismatches + " of " + cases + " random ops wrong. First one:\n" + first);
    }
}
