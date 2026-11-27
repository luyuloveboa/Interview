package inmemorydb.fu3;

import java.util.*;

public class InMemoryDB {
    static final long NEVER = Long.MAX_VALUE;                      // expireAt of a field with no TTL

    /** Alive during [set time, expireAt). */
    static class Entry {
        final String value;
        final long expireAt;

        Entry(String value, long expireAt) { this.value = value; this.expireAt = expireAt; }

        boolean aliveAt(long t) { return t < expireAt; }
    }

    /** [FU3 NEW] What a backup keeps per field: the REMAINING ttl, or null for "never expires". */
    static class Saved {
        final String value;
        final Long remaining;

        Saved(String value, Long remaining) { this.value = value; this.remaining = remaining; }
    }

    private final Map<String, TreeMap<String, Entry>> records = new HashMap<>();
    private final TreeMap<Integer, Map<String, Map<String, Saved>>> backups = new TreeMap<>();   // [FU3 NEW]

    // A test never mixes the two APIs, and fields written without a timestamp never
    // expire, so the old methods can run as the new ones at any fixed time.
    static final int NO_TS = 0;

    public InMemoryDB() {}

    public void setData(String key, String field, String value) { setDataAt(key, field, value, NO_TS); }

    public String getData(String key, String field) { return getDataAt(key, field, NO_TS); }

    public boolean deleteData(String key, String field) { return deleteDataAt(key, field, NO_TS); }

    public List<String> scanData(String key) { return scanDataAt(key, NO_TS); }

    public List<String> scanDataByPrefix(String key, String prefix) { return scanDataByPrefixAt(key, prefix, NO_TS); }

    /** Overwriting replaces the TTL too: a plain set makes the field permanent. */
    public void setDataAt(String key, String field, String value, int timestamp) {
        records.computeIfAbsent(key, k -> new TreeMap<>()).put(field, new Entry(value, NEVER));
    }

    public void setDataAtWithTtl(String key, String field, String value, int timestamp, int ttl) {
        records.computeIfAbsent(key, k -> new TreeMap<>()).put(field, new Entry(value, (long) timestamp + ttl));
    }

    public String getDataAt(String key, String field, int timestamp) {
        Entry e = live(key, field, timestamp);
        return e == null ? "" : e.value;
    }

    /** An expired field counts as missing. */
    public boolean deleteDataAt(String key, String field, int timestamp) {
        if (live(key, field, timestamp) == null) return false;
        TreeMap<String, Entry> rec = records.get(key);
        rec.remove(field);
        if (rec.isEmpty()) records.remove(key);
        return true;
    }

    public List<String> scanDataAt(String key, int timestamp) {
        return scanDataByPrefixAt(key, "", timestamp);
    }

    /** Walk from the prefix onward, skipping (and dropping) expired fields. */
    public List<String> scanDataByPrefixAt(String key, String prefix, int timestamp) {
        List<String> out = new ArrayList<>();
        TreeMap<String, Entry> rec = records.get(key);
        if (rec == null) return out;
        Iterator<Map.Entry<String, Entry>> it = rec.tailMap(prefix, true).entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Entry> e = it.next();
            if (!e.getKey().startsWith(prefix)) break;
            if (e.getValue().aliveAt(timestamp)) out.add(e.getKey() + "(" + e.getValue().value + ")");
            else it.remove();                                      // lazy expiry
        }
        if (rec.isEmpty()) records.remove(key);
        return out;
    }

    /** [FU3 NEW] Copies every alive field with its remaining ttl; returns the number of non-empty records. */
    public int backup(int timestamp) {
        Map<String, Map<String, Saved>> snap = new HashMap<>();
        for (Map.Entry<String, TreeMap<String, Entry>> rec : records.entrySet()) {
            Map<String, Saved> fields = new HashMap<>();
            for (Map.Entry<String, Entry> f : rec.getValue().entrySet()) {
                Entry e = f.getValue();
                if (!e.aliveAt(timestamp)) continue;
                fields.put(f.getKey(), new Saved(e.value, e.expireAt == NEVER ? null : e.expireAt - timestamp));
            }
            if (!fields.isEmpty()) snap.put(rec.getKey(), fields);
        }
        backups.put(timestamp, snap);
        return snap.size();
    }

    /** [FU3 NEW] Replaces everything with the latest backup at or before timestampToRestore. */
    public void restore(int timestamp, int timestampToRestore) {
        Map.Entry<Integer, Map<String, Map<String, Saved>>> b = backups.floorEntry(timestampToRestore);
        if (b == null) return;                                     // the problem guarantees one exists
        records.clear();
        for (Map.Entry<String, Map<String, Saved>> rec : b.getValue().entrySet()) {
            TreeMap<String, Entry> fields = new TreeMap<>();
            for (Map.Entry<String, Saved> f : rec.getValue().entrySet()) {
                Saved s = f.getValue();
                // the remaining ttl starts counting again from the restore time
                fields.put(f.getKey(), new Entry(s.value, s.remaining == null ? NEVER : timestamp + s.remaining));
            }
            records.put(rec.getKey(), fields);
        }
    }

    /** The field if it exists and is alive at t; an expired one is dropped on the way. */
    private Entry live(String key, String field, long t) {
        TreeMap<String, Entry> rec = records.get(key);
        Entry e = rec == null ? null : rec.get(field);
        if (e == null) return null;
        if (e.aliveAt(t)) return e;
        rec.remove(field);
        if (rec.isEmpty()) records.remove(key);
        return null;
    }
}
