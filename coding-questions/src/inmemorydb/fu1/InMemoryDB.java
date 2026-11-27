package inmemorydb.fu1;

import java.util.*;

public class InMemoryDB {
    // [FU1 CHANGED] TreeMap instead of HashMap: fields come out sorted, and all fields with a
    // given prefix sit next to each other starting at the prefix itself.
    private final Map<String, TreeMap<String, String>> records = new HashMap<>();

    public InMemoryDB() {}

    public void setData(String key, String field, String value) {
        records.computeIfAbsent(key, k -> new TreeMap<>()).put(field, value);   // [FU1 CHANGED]
    }

    public String getData(String key, String field) {
        Map<String, String> rec = records.get(key);
        return rec == null ? "" : rec.getOrDefault(field, "");
    }

    public boolean deleteData(String key, String field) {
        Map<String, String> rec = records.get(key);
        if (rec == null || rec.remove(field) == null) return false;
        if (rec.isEmpty()) records.remove(key);                    // only keep non-empty records
        return true;
    }

    /** [FU1 NEW] Every string starts with "". */
    public List<String> scanData(String key) {
        return scanDataByPrefix(key, "");
    }

    /** [FU1 NEW] Walk from the prefix onward and stop at the first field that doesn't match. */
    public List<String> scanDataByPrefix(String key, String prefix) {
        List<String> out = new ArrayList<>();
        TreeMap<String, String> rec = records.get(key);
        if (rec == null) return out;
        for (Map.Entry<String, String> e : rec.tailMap(prefix, true).entrySet()) {
            if (!e.getKey().startsWith(prefix)) break;
            out.add(e.getKey() + "(" + e.getValue() + ")");
        }
        return out;
    }
}
