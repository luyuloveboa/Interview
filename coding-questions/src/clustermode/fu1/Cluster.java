package clustermode.fu1;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/* ===================== Provided API (simulated here so it runs) ===================== */

/** In-memory network: one mailbox per worker, plus traffic counters. */
class Network {
    final List<BlockingQueue<String>> mailboxes = new ArrayList<>();
    final AtomicLong messages = new AtomicLong(), chars = new AtomicLong();

    Network(int k) { for (int i = 0; i < k; i++) mailboxes.add(new LinkedBlockingQueue<>()); }
}

class Worker {
    protected final int workerId;
    private final Network net;

    Worker(int workerId, Network net) { this.workerId = workerId; this.net = net; }

    /** Sends a payload to the target worker asynchronously. Do NOT modify. */
    public void sendAsyncMessage(int targetWorkerId, String payload) {
        net.messages.incrementAndGet();
        net.chars.addAndGet(payload.length());
        net.mailboxes.get(targetWorkerId).add(payload);
    }

    /** Receives the next payload from this worker's mailbox (blocks). Do NOT modify. */
    public String receive() {
        try { return net.mailboxes.get(workerId).take(); }
        catch (InterruptedException e) { throw new RuntimeException(e); }
    }
}

/* ===================== Our code ===================== */

class ClusterWorker extends Worker {
    final int k;
    final int[] data;                                    // this worker's slice only
    private final List<String> early = new ArrayList<>(); // messages that arrived before we asked for them

    ClusterWorker(int id, Network net, int k, int[] data) { super(id, net); this.k = k; this.data = data; }

    // ---------------- Mode ----------------
    // Shuffle by value: every value has exactly one owner worker. Each worker sends its local
    // (value, count) pairs to the owners, so owners know EXACT global counts for their values.
    // Raw data never moves; traffic is O(distinct values per worker).

    /** Returns the global mode on worker 0, null on other workers. */
    Integer runMode() {
        // Phase 1: local counts -> one "C|v:c,v:c,..." message to every owner (even if empty,
        //          so each owner knows when it has heard from all k workers).
        Map<Integer, Integer> local = new HashMap<>();
        for (int v : data) local.merge(v, 1, Integer::sum);
        StringBuilder[] out = new StringBuilder[k];
        for (int i = 0; i < k; i++) out[i] = new StringBuilder();
        for (Map.Entry<Integer, Integer> e : local.entrySet()) {
            StringBuilder sb = out[owner(e.getKey())];
            if (sb.length() > 0) sb.append(',');
            sb.append(e.getKey()).append(':').append(e.getValue());
        }
        for (int i = 0; i < k; i++) sendAsyncMessage(i, "C|" + out[i]);

        // Phase 2: as an owner, add up the global counts of my values; send my best one to worker 0.
        Map<Integer, Integer> global = new HashMap<>();
        for (int i = 0; i < k; i++)
            for (String vc : items(receive("C"))) {
                String[] p = vc.split(":");
                global.merge(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer::sum);
            }
        int[] best = null;                               // {value, count}
        for (Map.Entry<Integer, Integer> e : global.entrySet())
            best = better(best, new int[]{e.getKey(), e.getValue()});
        sendAsyncMessage(0, best == null ? "B|" : "B|" + best[0] + ":" + best[1]);

        // Phase 3 (worker 0 only): best of the k owners' bests.
        if (workerId != 0) return null;
        best = null;
        for (int i = 0; i < k; i++)
            for (String vc : items(receive("B"))) {
                String[] p = vc.split(":");
                best = better(best, new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1])});
            }
        return best[0];
    }

    /** Higher count wins; on a tie, the smaller value wins. */
    static int[] better(int[] a, int[] b) {
        if (a == null) return b;
        return (b[1] > a[1] || (b[1] == a[1] && b[0] < a[0])) ? b : a;
    }

    /** Which worker owns value v (hashed, so clustered values still spread out). */
    int owner(int v) { return Math.floorMod(v * 0x9E3779B1, k); }

    // ---------------- [FU1 NEW] Median ----------------
    // Binary search on the VALUE range [-1e9, 1e9]. Worker 0 broadcasts a guess x; every worker
    // replies how many of its values are <= x. ~31 rounds, 2 numbers per message: O(k log range).
    // Both middle ranks are searched in the same rounds (they are equal when n is odd).

    static final long MIN = -1_000_000_000L, MAX = 1_000_000_000L;

    /** Returns the global median on worker 0, null on other workers. */
    Double runMedian() {
        int[] sorted = data.clone();
        Arrays.sort(sorted);                             // once, so each query is O(log n_i)

        if (workerId != 0) {                             // follower: answer queries until "Q|stop"
            sendAsyncMessage(0, "N|" + sorted.length);
            while (true) {
                String q = receive("Q");
                if (q.equals("Q|stop")) return null;
                String[] x = q.substring(2).split(",");
                sendAsyncMessage(0, "R|" + countLE(sorted, Long.parseLong(x[0])) + "," + countLE(sorted, Long.parseLong(x[1])));
            }
        }

        // worker 0: coordinator (answers its own queries locally)
        long n = sorted.length;
        for (int i = 1; i < k; i++) n += Long.parseLong(receive("N").substring(2));
        long r1 = (n - 1) / 2, r2 = n / 2;               // 0-indexed ranks of the middle value(s)
        long lo1 = MIN, hi1 = MAX, lo2 = MIN, hi2 = MAX; // find smallest x with countLE(x) >= rank + 1
        while (lo1 < hi1 || lo2 < hi2) {
            long m1 = Math.floorDiv(lo1 + hi1, 2), m2 = Math.floorDiv(lo2 + hi2, 2);
            for (int i = 1; i < k; i++) sendAsyncMessage(i, "Q|" + m1 + "," + m2);
            long c1 = countLE(sorted, m1), c2 = countLE(sorted, m2);
            for (int i = 1; i < k; i++) {
                String[] c = receive("R").substring(2).split(",");
                c1 += Long.parseLong(c[0]);
                c2 += Long.parseLong(c[1]);
            }
            if (c1 >= r1 + 1) hi1 = m1; else lo1 = m1 + 1;
            if (c2 >= r2 + 1) hi2 = m2; else lo2 = m2 + 1;
        }
        for (int i = 1; i < k; i++) sendAsyncMessage(i, "Q|stop");
        return (lo1 + lo2) / 2.0;                        // long sum: no int overflow
    }

    /** Number of values <= x in a sorted array. */
    static int countLE(int[] a, long x) {
        int lo = 0, hi = a.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (a[mid] <= x) lo = mid + 1; else hi = mid;
        }
        return lo;
    }

    // ---------------- messaging helpers ----------------

    /** Next message with this tag ("C", "B", ...). Messages of other phases are kept for later. */
    String receive(String tag) {
        for (int i = 0; i < early.size(); i++)
            if (early.get(i).startsWith(tag + "|")) return early.remove(i);
        while (true) {
            String msg = receive();
            if (msg.startsWith(tag + "|")) return msg;
            early.add(msg);                              // e.g. a fast worker's phase-2 message
        }
    }

    /** "C|a,b,c" -> [a, b, c];  "C|" -> [] */
    static List<String> items(String msg) {
        String body = msg.substring(msg.indexOf('|') + 1);
        return body.isEmpty() ? List.of() : Arrays.asList(body.split(","));
    }
}

public class Cluster {
    private final List<ClusterWorker> workers = new ArrayList<>();
    final Network net;

    /** Splits data evenly into k contiguous slices, one per worker. */
    public Cluster(int[] data, int k) {
        net = new Network(k);
        for (int i = 0; i < k; i++) {
            int from = (int) ((long) data.length * i / k), to = (int) ((long) data.length * (i + 1) / k);
            workers.add(new ClusterWorker(i, net, k, Arrays.copyOfRange(data, from, to)));
        }
    }

    public int findMode() { return runOnAllWorkers(ClusterWorker::runMode); }

    public double findMedian() { return runOnAllWorkers(ClusterWorker::runMedian); }   // [FU1 NEW]

    /** Total messages sent so far (used by the tests to check network usage). */
    public long messagesSent() { return net.messages.get(); }

    /** Runs the algorithm on every worker concurrently; returns worker 0's result. */
    private <T> T runOnAllWorkers(Function<ClusterWorker, T> algorithm) {
        ExecutorService pool = Executors.newFixedThreadPool(workers.size());
        try {
            List<Future<T>> results = new ArrayList<>();
            for (ClusterWorker w : workers) results.add(pool.submit(() -> algorithm.apply(w)));
            for (Future<T> f : results) f.get();         // wait for everyone to finish
            return results.get(0).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } finally {
            pool.shutdown();
        }
    }
}
