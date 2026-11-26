import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A tiny in-memory key-value store with per-key TTL.
 *
 * Entry point: {@link #main(String[])} demos the API.
 * Core API: put / get / delete / size.
 * Data model: one ConcurrentHashMap<String, Entry>; Entry = (value, expiresAtMillis).
 * Expiry is enforced two ways: lazily on get(), and eagerly by a background sweeper.
 */
public class KVStore {

    private static final long NO_EXPIRY = Long.MAX_VALUE;

    private static final class Entry {
        final String value;
        final long expiresAtMillis; // NO_EXPIRY means the key never expires

        Entry(String value, long expiresAtMillis) {
            this.value = value;
            this.expiresAtMillis = expiresAtMillis;
        }

        boolean isExpired(long nowMillis) {
            return nowMillis >= expiresAtMillis;
        }
    }

    private final Map<String, Entry> data = new ConcurrentHashMap<>();
    private final ScheduledExecutorService sweeper =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "kv-sweeper");
                t.setDaemon(true);
                return t;
            });

    public KVStore() {
        // Eager expiry: sweep once a second. Lazy expiry on get() covers
        // the gap between sweeps.
        sweeper.scheduleAtFixedRate(this::sweepExpired, 1, 1, TimeUnit.SECONDS);
    }

    /** Stores value under key. ttlSeconds <= 0 means the key never expires. */
    public void put(String key, String value, long ttlSeconds) {
        long expiresAt = ttlSeconds <= 0
                ? NO_EXPIRY
                : System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(ttlSeconds);
        data.put(key, new Entry(value, expiresAt));
    }

    /** Returns the value, or null if the key is missing or expired. */
    public String get(String key) {
        Entry entry = data.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired(System.currentTimeMillis())) {
            data.remove(key, entry); // lazy expiry: only the exact entry we read
            return null;
        }
        return entry.value;
    }

    /** Removes the key. Returns true if a live entry was removed. */
    public boolean delete(String key) {
        return data.remove(key) != null;
    }

    /** Number of live (non-expired) entries. O(n): scans the whole map. */
    public int size() {
        long now = System.currentTimeMillis();
        int count = 0;
        for (Entry entry : data.values()) {
            if (!entry.isExpired(now)) {
                count++;
            }
        }
        return count;
    }

    /** Stops the background sweeper. After this, only lazy expiry on get() applies. */
    public void close() {
        sweeper.shutdown();
    }

    private void sweepExpired() {
        long now = System.currentTimeMillis();
        data.entrySet().removeIf(e -> e.getValue().isExpired(now));
    }

    public static void main(String[] args) throws InterruptedException {
        KVStore store = new KVStore();
        store.put("session", "abc", 2);   // expires in 2s
        store.put("config", "dark", 0);   // never expires
        System.out.println(store.get("session")); // abc
        System.out.println(store.size());         // 2
        Thread.sleep(2500);
        System.out.println(store.get("session")); // null (expired)
        System.out.println(store.size());         // 1
        store.close();
    }
}
