package clustermode.fu1;

import java.util.*;

class Worker {
    private final int workerId;
    private final int k;
    private final List<Integer> localData;
    private final Cluster cluster;

    public Worker(int workerId, int k, List<Integer> localData, Cluster cluster) {
        this.workerId = workerId;
        this.k = k;
        this.localData = localData;
        this.cluster = cluster;
    }

    /*
     * Sends a string-represented payload data to the specified worker
     * asynchronously. You should NOT modify this method.
     */
    public void sendAsyncMessage(int targetWorkerId, String payload) {
        cluster.sendAsyncMessage(targetWorkerId, payload);
    }

    /*
     * Receives a string-represented payload data from the worker's mailbox. You
     * should NOT modify this method.
     */
    public String receive() {
        return cluster.receive(workerId);
    }

    /** receive() returns "" when the mailbox is empty; in this phase-by-phase design that is a bug, so fail fast. */
    private String next() {
        String msg = receive();
        if (msg.isEmpty()) throw new IllegalStateException("worker " + workerId + ": expected a message, mailbox empty");
        return msg;
    }

    // ===================== Mode =====================

    /** Phase 1: send each owner ONE batched message "v:c,v:c,..." (possibly empty). No DONE needed. */
    public void scatterFreq() {
        Map<Integer, Integer> localFreq = new HashMap<>();
        for (int value : localData) localFreq.merge(value, 1, Integer::sum);

        StringBuilder[] batch = new StringBuilder[k];
        for (int i = 0; i < k; i++) batch[i] = new StringBuilder("F");
        for (Map.Entry<Integer, Integer> e : localFreq.entrySet())
            batch[Math.floorMod(e.getKey(), k)].append(' ').append(e.getKey()).append(':').append(e.getValue());
        for (int i = 0; i < k; i++) sendAsyncMessage(i, batch[i].toString());
    }

    /** Phase 2: exactly k messages arrive (one per sender); return {value, count} of my best value, or null. */
    public long[] aggregateFreq() {
        Map<Integer, Long> ownedFreq = new HashMap<>();
        for (int i = 0; i < k; i++) {
            String[] parts = next().split(" ");
            for (int j = 1; j < parts.length; j++) {
                String[] vc = parts[j].split(":");
                ownedFreq.merge(Integer.parseInt(vc[0]), Long.parseLong(vc[1]), Long::sum);
            }
        }
        long[] best = null;
        for (Map.Entry<Integer, Long> e : ownedFreq.entrySet())
            best = better(best, new long[]{e.getKey(), e.getValue()});
        return best;
    }

    /** Phase 3: every worker (worker 0 included) sends its best to worker 0. */
    public void reportLocal(long[] localMode) {
        sendAsyncMessage(0, localMode == null ? "B" : "B " + localMode[0] + " " + localMode[1]);
    }

    /** Phase 4 (worker 0): pick the best of the k reports. */
    public int gatherGlobal() {
        long[] best = null;
        for (int i = 0; i < k; i++) {
            String[] parts = next().split(" ");
            if (parts.length == 3) best = better(best, new long[]{Long.parseLong(parts[1]), Long.parseLong(parts[2])});
        }
        return (int) best[0];
    }

    /** Higher count wins; on a tie, the smaller value wins. */
    private static long[] better(long[] a, long[] b) {
        if (a == null) return b;
        return (b[1] > a[1] || (b[1] == a[1] && b[0] < a[0])) ? b : a;
    }

    // ===================== [FU1 NEW] Median =====================
    // Binary search on the value range. Each round: worker 0 broadcasts two guesses, every worker
    // replies how many of its values are <= each guess. ~31 rounds, O(k log range) messages total.

    private int[] sorted;                                          // local data, sorted once
    private long lo1, hi1, lo2, hi2, rank1, rank2;                 // search state (worker 0 only)

    public void reportSize() {
        sorted = localData.stream().mapToInt(Integer::intValue).sorted().toArray();
        sendAsyncMessage(0, "N " + sorted.length);
    }

    /** Worker 0: total size -> the 0-indexed ranks of the middle value(s). */
    public void startSearch() {
        long n = 0;
        for (int i = 0; i < k; i++) n += Long.parseLong(next().split(" ")[1]);
        rank1 = (n - 1) / 2;
        rank2 = n / 2;
        lo1 = lo2 = -1_000_000_000L;
        hi1 = hi2 = 1_000_000_000L;
    }

    public boolean searching() { return lo1 < hi1 || lo2 < hi2; }

    public void broadcastQuery() {
        String q = "Q " + Math.floorDiv(lo1 + hi1, 2) + " " + Math.floorDiv(lo2 + hi2, 2);
        for (int i = 0; i < k; i++) sendAsyncMessage(i, q);
    }

    public void answerQuery() {
        String[] q = next().split(" ");
        sendAsyncMessage(0, "C " + countLE(Long.parseLong(q[1])) + " " + countLE(Long.parseLong(q[2])));
    }

    /** Worker 0: add up the k answers and move both searches. */
    public void collectCounts() {
        long m1 = Math.floorDiv(lo1 + hi1, 2), m2 = Math.floorDiv(lo2 + hi2, 2), c1 = 0, c2 = 0;
        for (int i = 0; i < k; i++) {
            String[] c = next().split(" ");
            c1 += Long.parseLong(c[1]);
            c2 += Long.parseLong(c[2]);
        }
        if (c1 >= rank1 + 1) hi1 = m1; else lo1 = m1 + 1;          // smallest x with count(<= x) >= rank + 1
        if (c2 >= rank2 + 1) hi2 = m2; else lo2 = m2 + 1;
    }

    public double median() { return (lo1 + lo2) / 2.0; }          // long sum: no overflow

    private int countLE(long x) {                                  // # local values <= x
        int lo = 0, hi = sorted.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (sorted[mid] <= x) lo = mid + 1; else hi = mid;
        }
        return lo;
    }
}

public class Cluster {
    private final Map<Integer, List<String>> mailboxes;
    private final Map<Integer, Integer> readIndices;

    private final int k;
    private final List<List<Integer>> shards;
    private final Worker[] workers;

    public Cluster(List<Integer> data, int k) {
        this.k = k;

        this.shards = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            shards.add(new ArrayList<>());
        }

        // Distribute data evenly across workers
        int totalSize = data.size();
        int baseSize = totalSize / k;
        int remainder = totalSize % k;

        int index = 0;
        for (int w = 0; w < k; w++) {
            int chunkSize = baseSize + (w < remainder ? 1 : 0);
            for (int j = 0; j < chunkSize; j++) {
                shards.get(w).add(data.get(index));
                index++;
            }
        }

        this.mailboxes = new HashMap<>();
        this.readIndices = new HashMap<>();
        for (int i = 0; i < k; i++) {
            mailboxes.put(i, new ArrayList<>());
            readIndices.put(i, 0);
        }

        this.workers = new Worker[k];
        for (int i = 0; i < k; i++) {
            workers[i] = new Worker(i, k, shards.get(i), this);
        }
    }

    public void sendAsyncMessage(int targetWorkerId, String payload) {
        mailboxes.get(targetWorkerId).add(payload);
    }

    public String receive(int workerId) {
        List<String> myMailbox = mailboxes.get(workerId);
        int idx = readIndices.get(workerId);
        if (idx < myMailbox.size()) {
            readIndices.put(workerId, idx + 1);
            return myMailbox.get(idx);
        }

        return "";
    }

    /** Total messages sent so far (used by the tests to check network usage). */
    public long messagesSent() {
        long total = 0;
        for (List<String> box : mailboxes.values()) total += box.size();
        return total;
    }

    /** Each phase runs on every worker before the next phase starts. */
    public int findMode() {
        for (Worker w : workers) w.scatterFreq();
        long[][] best = new long[k][];
        for (int i = 0; i < k; i++) best[i] = workers[i].aggregateFreq();
        for (int i = 0; i < k; i++) workers[i].reportLocal(best[i]);
        return workers[0].gatherGlobal();
    }

    /** [FU1 NEW] Every round: worker 0 asks, everyone answers, worker 0 collects. */
    public double findMedian() {
        for (Worker w : workers) w.reportSize();
        workers[0].startSearch();
        while (workers[0].searching()) {
            workers[0].broadcastQuery();
            for (Worker w : workers) w.answerQuery();
            workers[0].collectCounts();
        }
        return workers[0].median();
    }
}
