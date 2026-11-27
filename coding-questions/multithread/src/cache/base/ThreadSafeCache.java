package cache.base;

/**
 * P05 — ThreadSafeCache with ReadWriteLock (Medium).
 *
 * A thread-safe key-value cache. get(key) returns the value or null if the
 * key is absent; put(key, value) writes or overwrites; size() returns the
 * number of distinct keys.
 *
 * The implementation MUST use a ReadWriteLock: reads (get, size) take the
 * read lock, writes (put) take the write lock. A plain HashMap with no lock
 * loses data under concurrent writes, and synchronized on every method is
 * correct but serializes reads, losing the read concurrency this problem
 * wants.
 *
 * Fill in get(), put() and size() below, then run the tests from
 * coding-questions/multithread/:
 *   ./run-tests.sh          (macOS / Linux)
 *   .\run-tests.ps1         (Windows PowerShell)
 */
public class ThreadSafeCache {

    public String get(String key) {
        // TODO: implement this method
        throw new UnsupportedOperationException("TODO");
    }

    public void put(String key, String value) {
        // TODO: implement this method
        throw new UnsupportedOperationException("TODO");
    }

    public int size() {
        // TODO: implement this method
        throw new UnsupportedOperationException("TODO");
    }
}
