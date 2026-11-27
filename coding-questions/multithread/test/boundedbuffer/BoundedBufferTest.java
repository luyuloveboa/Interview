package boundedbuffer;

import java.util.*;
import java.util.concurrent.atomic.*;
import testutil.Check;
import boundedbuffer.base.BoundedBuffer;

public class BoundedBufferTest {
    public static void main(String[] args) { run(); }

    public static void run() {
        base();
        blocking();
        concurrent();
    }

    // put() / take() throw InterruptedException; in a test an interrupt is a failure.
    static void put(BoundedBuffer<Integer> buf, int item) {
        try {
            buf.put(item);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while putting " + item, e);
        }
    }

    static int take(BoundedBuffer<Integer> buf) {
        try {
            return buf.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while taking", e);
        }
    }

    static void sleepUnchecked(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while sleeping", e);
        }
    }

    /** Joins with a timeout; returns false if the thread is still alive. */
    static boolean joinUnchecked(Thread t, long timeoutMs) {
        try {
            t.join(timeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while joining " + t.getName(), e);
        }
        return !t.isAlive();
    }

    static void base() {
        Check.section("Bounded buffer: base");
        Check.eq("FIFO cap 2: put 1,2 take -> 1,2", () -> {
            BoundedBuffer<Integer> buf = new BoundedBuffer<>(2);
            put(buf, 1);
            put(buf, 2);
            return List.of(take(buf), take(buf));
        }, List.of(1, 2));
        Check.eq("FIFO cap 1: put 1 take -> 1", () -> {
            BoundedBuffer<Integer> buf = new BoundedBuffer<>(1);
            put(buf, 1);
            return take(buf);
        }, 1);
        Check.eq("size: 0,1,2,1,0 as items come and go", () -> {
            BoundedBuffer<Integer> buf = new BoundedBuffer<>(2);
            List<Integer> sizes = new ArrayList<>();
            sizes.add(buf.size());
            put(buf, 1);
            sizes.add(buf.size());
            put(buf, 2);
            sizes.add(buf.size());
            take(buf);
            sizes.add(buf.size());
            take(buf);
            sizes.add(buf.size());
            return sizes;
        }, List.of(0, 1, 2, 1, 0));
        Check.eq("FIFO interleaved cap 3: put 1,2,3 take 1 put 4 take -> 1,2,3,4", () -> {
            BoundedBuffer<Integer> buf = new BoundedBuffer<>(3);
            put(buf, 1);
            put(buf, 2);
            put(buf, 3);
            List<Integer> taken = new ArrayList<>();
            taken.add(take(buf));
            put(buf, 4);
            taken.add(take(buf));
            taken.add(take(buf));
            taken.add(take(buf));
            return taken;
        }, List.of(1, 2, 3, 4));
        Check.eq("drain and refill cap 5 twice", () -> {
            BoundedBuffer<Integer> buf = new BoundedBuffer<>(5);
            List<Integer> taken = new ArrayList<>();
            for (int round = 0; round < 2; round++) {
                for (int i = 1; i <= 5; i++) put(buf, round * 5 + i);
                for (int i = 0; i < 5; i++) taken.add(take(buf));
            }
            return taken;
        }, List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
    }

    static void blocking() {
        Check.section("Bounded buffer: blocking");
        Check.eq("put blocks when full, unblocks after take", () -> {
            BoundedBuffer<Integer> buf = new BoundedBuffer<>(1);
            put(buf, 1);
            AtomicBoolean putReturned = new AtomicBoolean(false);
            Thread putter = new Thread(() -> {
                put(buf, 2);
                putReturned.set(true);
            }, "blocked-putter");
            putter.setDaemon(true);
            putter.start();
            sleepUnchecked(200);
            if (!putter.isAlive() || putReturned.get())
                return "second put returned while buffer full (should have blocked)";
            if (take(buf) != 1) return "first take did not return 1";
            if (!joinUnchecked(putter, 1000))
                return "second put still blocked after a take freed space (deadlock?)";
            if (take(buf) != 2) return "second take did not return 2";
            return "ok";
        }, "ok");
        Check.eq("take blocks when empty, returns item after put", () -> {
            BoundedBuffer<Integer> buf = new BoundedBuffer<>(2);
            AtomicReference<Integer> taken = new AtomicReference<>();
            Thread taker = new Thread(() -> taken.set(take(buf)), "blocked-taker");
            taker.setDaemon(true);
            taker.start();
            sleepUnchecked(200);
            if (!taker.isAlive())
                return "take returned on an empty buffer (should have blocked)";
            put(buf, 42);
            if (!joinUnchecked(taker, 1000))
                return "take still blocked after a put (deadlock?)";
            if (taken.get() == null || taken.get() != 42)
                return "take returned " + taken.get() + ", expected 42";
            return "ok";
        }, "ok");
    }

    static void concurrent() {
        Check.section("Bounded buffer: concurrent");
        Check.eq("2 producers x 50 + 2 consumers: every item exactly once", () ->
                runProducersConsumers(2, 2, 50, 2), "ok");
        Check.eq("capacity never exceeded under contention (cap 1, 8 producers)", () ->
                capacityNeverExceeded(), "ok");
        Check.eq("4 producers x 25 + 4 consumers, 3 rounds: every item exactly once", () -> {
            for (int round = 0; round < 3; round++) {
                String result = runProducersConsumers(4, 4, 25, 2);
                if (!"ok".equals(result)) return "round " + round + ": " + result;
            }
            return "ok";
        }, "ok");
    }

    /**
     * producerCount producers each put itemsPerProducer distinct values;
     * consumerCount consumers take total / consumerCount each. Returns "ok" or
     * a description of what went wrong. Every blocking call runs on a helper
     * thread joined with a timeout, so a deadlock fails this check instead of
     * hanging the suite.
     */
    static String runProducersConsumers(int producerCount, int consumerCount,
            int itemsPerProducer, int capacity) {
        int total = producerCount * itemsPerProducer;
        int itemsPerConsumer = total / consumerCount;
        BoundedBuffer<Integer> buf = new BoundedBuffer<>(capacity);
        List<Integer> taken = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<String> error = new AtomicReference<>();
        List<Thread> threads = new ArrayList<>();

        for (int p = 0; p < producerCount; p++) {
            int producerId = p;
            Thread producer = new Thread(() -> {
                try {
                    for (int i = 0; i < itemsPerProducer; i++)
                        buf.put(producerId * 1000 + i);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    error.compareAndSet(null, "producer " + producerId + " interrupted");
                }
            }, "producer-" + p);
            producer.setDaemon(true);
            threads.add(producer);
        }
        for (int c = 0; c < consumerCount; c++) {
            Thread consumer = new Thread(() -> {
                try {
                    for (int i = 0; i < itemsPerConsumer; i++)
                        taken.add(buf.take());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    error.compareAndSet(null, "consumer interrupted");
                }
            }, "consumer-" + c);
            consumer.setDaemon(true);
            threads.add(consumer);
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) {
            if (!joinUnchecked(t, 1500))
                return "deadlock: " + t.getName() + " still blocked after 1500 ms";
        }
        if (error.get() != null) return error.get();
        if (taken.size() != total)
            return "took " + taken.size() + " items, expected " + total;
        List<Integer> expected = new ArrayList<>();
        for (int p = 0; p < producerCount; p++)
            for (int i = 0; i < itemsPerProducer; i++)
                expected.add(p * 1000 + i);
        List<Integer> sortedTaken = new ArrayList<>(taken);
        Collections.sort(sortedTaken);
        Collections.sort(expected);
        if (!sortedTaken.equals(expected)) {
            Set<Integer> seen = new HashSet<>(taken);
            List<Integer> missing = new ArrayList<>(expected);
            missing.removeAll(seen);
            List<Integer> extra = new ArrayList<>(taken);
            extra.removeAll(expected);
            return "wrong multiset: missing " + missing.subList(0, Math.min(5, missing.size()))
                    + ", duplicates/extra " + extra.subList(0, Math.min(5, extra.size()));
        }
        if (buf.size() != 0) return "buffer not empty after all items taken, size() = " + buf.size();
        return "ok";
    }

    /**
     * Capacity 1, 8 producers each putting one item, one consumer taking with
     * 10 ms between takes, while a sampler polls size(). A correct buffer
     * never reports size() > 1; an if-guarded wait() lets several woken
     * producers put at once and overfills it, which the sampler observes
     * (the overfill lasts until the consumer's next take, ~10 ms).
     */
    static String capacityNeverExceeded() {
        BoundedBuffer<Integer> buf = new BoundedBuffer<>(1);
        int producers = 8;
        AtomicInteger maxSize = new AtomicInteger(0);
        AtomicBoolean stopSampler = new AtomicBoolean(false);
        Thread sampler = new Thread(() -> {
            while (!stopSampler.get()) {
                int size = buf.size();
                maxSize.accumulateAndGet(size, Math::max);
            }
        }, "size-sampler");
        sampler.setDaemon(true);
        AtomicReference<String> error = new AtomicReference<>();
        List<Thread> threads = new ArrayList<>();
        for (int p = 0; p < producers; p++) {
            int item = p;
            Thread producer = new Thread(() -> {
                try {
                    buf.put(item);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    error.compareAndSet(null, "producer interrupted");
                }
            }, "producer-" + p);
            producer.setDaemon(true);
            threads.add(producer);
        }
        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < producers; i++) {
                    buf.take();
                    Thread.sleep(10);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                error.compareAndSet(null, "consumer interrupted");
            }
        }, "consumer");
        consumer.setDaemon(true);
        threads.add(consumer);
        sampler.start();
        for (Thread t : threads) t.start();
        for (Thread t : threads) {
            if (!joinUnchecked(t, 1500)) {
                stopSampler.set(true);
                return "deadlock: " + t.getName() + " still blocked after 1500 ms";
            }
        }
        stopSampler.set(true);
        if (error.get() != null) return error.get();
        if (maxSize.get() > 1)
            return "size() reached " + maxSize.get() + " in a capacity-1 buffer (overfill)";
        return "ok";
    }
}
