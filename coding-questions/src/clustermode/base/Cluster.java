package clustermode.base;

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
}
