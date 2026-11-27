package connectionpool;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import testutil.Check;
import connectionpool.base.ConnectionPool;

public class ConnectionPoolTest {
    public static void main(String[] args) { run(); }

    public static void run() {
        base();
        concurrentStress();
    }

    static void base() {
        Check.section("Connection pool: base");

        // Spec example: size 2 — first two acquires are different ids, the released
        // id is reused by the next acquire.
        Check.eq("size 2: two acquires give different ids, released conn-0 is reused", () -> {
            ConnectionPool pool = new ConnectionPool(2);
            String first = acquire(pool);
            String second = acquire(pool);
            if (first.equals(second)) return "two acquires returned the same id: " + first;
            pool.release("conn-0");
            String third = acquire(pool);
            if (!"conn-0".equals(third)) return "after releasing conn-0, next acquire gave " + third;
            return "ok";
        }, "ok");

        Check.eq("size 1: acquire returns conn-0", () -> acquire(new ConnectionPool(1)), "conn-0");

        Check.eq("size 3: three acquires give conn-0, conn-1, conn-2", () -> {
            ConnectionPool pool = new ConnectionPool(3);
            List<String> ids = new ArrayList<>(Arrays.asList(acquire(pool), acquire(pool), acquire(pool)));
            Collections.sort(ids);
            return String.join(",", ids);
        }, "conn-0,conn-1,conn-2");

        Check.eq("size 2: released id is reused, full cycle lends both ids again", () -> {
            ConnectionPool pool = new ConnectionPool(2);
            String first = acquire(pool);
            String second = acquire(pool);
            if (first.equals(second)) return "two acquires returned the same id: " + first;
            pool.release(second);
            String third = acquire(pool);
            if (!third.equals(second)) return "after releasing " + second + ", next acquire gave " + third;
            pool.release(first);
            pool.release(second);
            List<String> ids = new ArrayList<>(Arrays.asList(acquire(pool), acquire(pool)));
            Collections.sort(ids);
            if (!ids.equals(Arrays.asList("conn-0", "conn-1"))) return "after releasing both, got " + ids;
            return "ok";
        }, "ok");

        // Spec example: size 1 — a second acquire blocks until someone releases.
        Check.eq("blocking: size-1 second acquire waits, then succeeds after release", () -> {
            ConnectionPool pool = new ConnectionPool(1);
            String first = acquire(pool);
            AcquireResult second = acquireAsync(pool);
            joinQuietly(second.thread, 200);
            if (second.error.get() != null) return "second acquire threw " + second.error.get();
            if (!second.thread.isAlive()) return "second acquire returned " + second.id.get() + " instead of blocking";
            pool.release(first);
            joinQuietly(second.thread, 1000);
            if (second.thread.isAlive()) return "second acquire still blocked after release";
            if (second.error.get() != null) return "second acquire threw " + second.error.get();
            if (!"conn-0".equals(second.id.get())) return "second acquire got " + second.id.get() + ", expected conn-0";
            return "ok";
        }, "ok");

        // Spec example: size 1 — a double release must not create an extra permit.
        // A bare Semaphore fails this: the third acquire succeeds immediately.
        Check.eq("double release must not create an extra permit", () -> {
            ConnectionPool pool = new ConnectionPool(1);
            String first = acquire(pool);
            pool.release(first);
            pool.release(first); // double release: must be a no-op
            String second = acquire(pool);
            if (!"conn-0".equals(second)) return "after double release, acquire gave " + second + ", expected conn-0";
            AcquireResult third = acquireAsync(pool);
            joinQuietly(third.thread, 200);
            if (third.error.get() != null) return "third acquire threw " + third.error.get();
            if (!third.thread.isAlive())
                return "third acquire succeeded immediately after a double release — an extra permit was created (got " + third.id.get() + ")";
            pool.release(second);
            joinQuietly(third.thread, 1000);
            if (third.thread.isAlive()) return "third acquire still blocked after release";
            if (third.error.get() != null) return "third acquire threw " + third.error.get();
            if (!"conn-0".equals(third.id.get())) return "third acquire got " + third.id.get() + ", expected conn-0";
            return "ok";
        }, "ok");

        // Releasing an id that was never borrowed (unknown id, out-of-range id, null)
        // must be a no-op, exactly like a double release.
        Check.eq("unknown-id release is a no-op (conn-99, never-borrowed id, null)", () -> {
            ConnectionPool pool = new ConnectionPool(1);
            String first = acquire(pool);
            pool.release("conn-99");
            pool.release("conn-1"); // valid-looking id, never borrowed from a size-1 pool
            pool.release(null);
            AcquireResult second = acquireAsync(pool);
            joinQuietly(second.thread, 200);
            if (second.error.get() != null) return "second acquire threw " + second.error.get();
            if (!second.thread.isAlive())
                return "second acquire succeeded after releasing ids that were never borrowed (got " + second.id.get() + ")";
            pool.release(first);
            joinQuietly(second.thread, 1000);
            if (second.thread.isAlive()) return "second acquire still blocked after releasing conn-0";
            if (second.error.get() != null) return "second acquire threw " + second.error.get();
            if (!"conn-0".equals(second.id.get())) return "second acquire got " + second.id.get() + ", expected conn-0";
            return "ok";
        }, "ok");

        // Max scale: all 100 ids lendable once each, the 101st acquire blocks.
        Check.eq("size 100: all 100 ids lendable once each, 101st acquire blocks", () -> {
            ConnectionPool pool = new ConnectionPool(100);
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                String id = acquire(pool);
                if (!ids.add(id)) return "id " + id + " handed out twice";
            }
            if (ids.size() != 100) return "only " + ids.size() + " distinct ids";
            AcquireResult extra = acquireAsync(pool);
            joinQuietly(extra.thread, 200);
            if (extra.error.get() != null) return "101st acquire threw " + extra.error.get();
            if (!extra.thread.isAlive()) return "101st acquire succeeded with all 100 connections out (got " + extra.id.get() + ")";
            pool.release("conn-42");
            joinQuietly(extra.thread, 1000);
            if (extra.thread.isAlive()) return "101st acquire still blocked after a release";
            if (extra.error.get() != null) return "101st acquire threw " + extra.error.get();
            if (!"conn-42".equals(extra.id.get())) return "101st acquire got " + extra.id.get() + ", expected conn-42";
            return "ok";
        }, "ok");

        // The pool itself creates no threads; nothing may be left behind after use.
        Check.eq("no leaked pool threads (pool creates no threads)", () -> {
            Set<String> before = poolThreadNames();
            ConnectionPool pool = new ConnectionPool(2);
            acquire(pool);
            acquire(pool);
            pool.release("conn-0");
            pool.release("conn-1");
            long deadline = System.currentTimeMillis() + 1000;
            Set<String> leaked;
            do {
                leaked = poolThreadNames();
                leaked.removeAll(before);
                if (leaked.isEmpty()) return "ok";
                try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            } while (System.currentTimeMillis() < deadline);
            return "threads still alive 1s after use: " + leaked;
        }, "ok");
    }

    /** Many threads acquiring/releasing: at most size held at once, no id lent twice, nothing leaked. */
    static void concurrentStress() {
        Check.section("Connection pool: concurrent stress");
        Check.eq("stress: 16 threads x 50 cycles, at most 4 held at once, no id lent twice", () -> {
            int size = 4;
            int threadCount = 16;
            int cycles = 50;
            ConnectionPool pool = new ConnectionPool(size);
            Set<String> held = Collections.synchronizedSet(new HashSet<>());
            AtomicInteger currentHeld = new AtomicInteger();
            AtomicInteger maxHeld = new AtomicInteger();
            AtomicReference<String> problem = new AtomicReference<>();
            CountDownLatch start = new CountDownLatch(1);
            List<Thread> workers = new ArrayList<>();
            for (int w = 0; w < threadCount; w++) {
                Thread worker = new Thread(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < cycles; i++) {
                            String id = pool.acquire();
                            if (id == null || !id.startsWith("conn-")) {
                                problem.compareAndSet(null, "bad id: " + id);
                                return;
                            }
                            if (!held.add(id)) {
                                problem.compareAndSet(null, "id " + id + " lent to two threads at once");
                                return;
                            }
                            int now = currentHeld.incrementAndGet();
                            maxHeld.accumulateAndGet(now, Math::max);
                            if (now > size) {
                                problem.compareAndSet(null, now + " held at once, size is " + size);
                                return;
                            }
                            Thread.sleep(1); // hold briefly so overlaps actually happen
                            held.remove(id);
                            currentHeld.decrementAndGet();
                            pool.release(id);
                        }
                    } catch (Throwable t) {
                        problem.compareAndSet(null, "worker threw " + t);
                    }
                }, "stress-" + w);
                worker.setDaemon(true);
                workers.add(worker);
                worker.start();
            }
            start.countDown();
            long deadline = System.currentTimeMillis() + 10000;
            for (Thread worker : workers) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) return "workers did not finish in 10s (deadlock?)";
                joinQuietly(worker, left);
                if (worker.isAlive()) return "worker " + worker.getName() + " did not finish in 10s (deadlock?)";
            }
            if (problem.get() != null) return problem.get();
            if (maxHeld.get() > size) return "max held " + maxHeld.get() + " exceeds size " + size;
            // Every connection must be back: all size ids re-acquirable immediately.
            for (int i = 0; i < size; i++) {
                AcquireResult reacquired = acquireAsync(pool);
                joinQuietly(reacquired.thread, 200);
                if (reacquired.thread.isAlive()) return "connection leaked after stress: only " + i + " of " + size + " re-acquirable";
                if (reacquired.error.get() != null) return "re-acquire threw " + reacquired.error.get();
                pool.release(reacquired.id.get());
            }
            return "ok";
        }, "ok");
    }

    /** acquire() without a throws declaration; an interrupt becomes a failure string via Check. */
    static String acquire(ConnectionPool pool) {
        try {
            return pool.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while acquiring", e);
        }
    }

    /** An acquire running in its own daemon thread, so a blocking acquire can be observed with join(). */
    static final class AcquireResult {
        Thread thread;
        AtomicReference<String> id = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
    }

    static AcquireResult acquireAsync(ConnectionPool pool) {
        AcquireResult result = new AcquireResult();
        result.thread = new Thread(() -> {
            try {
                result.id.set(pool.acquire());
            } catch (Throwable t) {
                result.error.set(t);
            }
        }, "acquire-helper");
        result.thread.setDaemon(true);
        result.thread.start();
        return result;
    }

    static void joinQuietly(Thread thread, long timeoutMs) {
        try {
            thread.join(timeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static Set<String> poolThreadNames() {
        Set<String> names = new HashSet<>();
        for (Thread t : Thread.getAllStackTraces().keySet())
            if (t.getName().startsWith("pool-")) names.add(t.getName());
        return names;
    }
}
