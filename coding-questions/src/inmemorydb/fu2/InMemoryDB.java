package inmemorydb.fu2;

import java.util.*;

public class InMemoryDB {
    static final long NEVER = Long.MAX_VALUE;                      // [FU2 NEW] expireAt of a field with no TTL

    /** [FU2 NEW] Alive during [set time, expireAt). */
    static class Entry {
        final String value;
        final long expireAt;

        Entry(String value, long expireAt) { this.value = value; this.expireAt = expireAt; }

        boolean aliveAt(long t) { return t < expireAt; }
    }

    // [FU2 CHANGED] values are Entry instead of String
    private final Map<String, TreeMap<String, Entry>> records = new HashMap<>();

    // [FU2 NEW] A test never mixes the two APIs, and fields written without a timestamp never
    // expire, so the old methods can run as the new ones at any fixed time.
    static final int NO_TS = 0;

    public InMemoryDB() {}

    // [FU2 CHANGED] the old API now forwards to the timestamped one
    public void setData(String key, String field, String value) { setDataAt(key, field, value, NO_TS); }

    public String getData(String key, String field) { return getDataAt(key, field, NO_TS); }

    public boolean deleteData(String key, String field) { return deleteDataAt(key, field, NO_TS); }

    public List<String> scanData(String key) { return scanDataAt(key, NO_TS); }

    public List<String> scanDataByPrefix(String key, String prefix) { return scanDataByPrefixAt(key, prefix, NO_TS); }

    /** [FU2 NEW] Overwriting replaces the TTL too: a plain set makes the field permanent. */
    public void setDataAt(String key, String field, String value, int timestamp) {
        records.computeIfAbsent(key, k -> new TreeMap<>()).put(field, new Entry(value, NEVER));
    }

    /** [FU2 NEW] */
    public void setDataAtWithTtl(String key, String field, String value, int timestamp, int ttl) {
        records.computeIfAbsent(key, k -> new TreeMap<>()).put(field, new Entry(value, (long) timestamp + ttl));
    }

    /** [FU2 NEW] */
    public String getDataAt(String key, String field, int timestamp) {
        Entry e = live(key, field, timestamp);
        return e == null ? "" : e.value;
    }

    /** [FU2 NEW] An expired field counts as missing. */
    public boolean deleteDataAt(String key, String field, int timestamp) {
        if (live(key, field, timestamp) == null) return false;
        TreeMap<String, Entry> rec = records.get(key);
        rec.remove(field);
        if (rec.isEmpty()) records.remove(key);
        return true;
    }

    /** [FU2 NEW] */
    public List<String> scanDataAt(String key, int timestamp) {
        return scanDataByPrefixAt(key, "", timestamp);
    }

    /** [FU2 NEW] Same walk as FU1, skipping (and dropping) expired fields. */
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

    /** [FU2 NEW] The field if it exists and is alive at t; an expired one is dropped on the way. */
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
