package inmemorydb.base;

import java.util.*;

public class InMemoryDB {
    private final Map<String, Map<String, String>> records = new HashMap<>();   // key -> (field -> value)

    public InMemoryDB() {}

    public void setData(String key, String field, String value) {
        records.computeIfAbsent(key, k -> new HashMap<>()).put(field, value);
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
}
