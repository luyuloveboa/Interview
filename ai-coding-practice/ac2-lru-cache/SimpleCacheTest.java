/**
 * Plain-JDK regression suite for SimpleCache (no JUnit needed).
 *
 * <p>Run: javac SimpleCache.java SimpleCacheTest.java && java SimpleCacheTest
 * Exits 0 only if every case passes; each case prints PASS or FAIL.
 */
public class SimpleCacheTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        checkBasicPutAndGet();
        checkMissingKeyReturnsNull();
        checkOverwriteUpdatesValue();
        checkEvictionRemovesLeastRecentlyUsed();
        checkGetRefreshesRecency();
        checkPutOnExistingKeyRefreshesRecency();
        checkCapacityOne();
        checkSizeAfterEviction();

        System.out.println("passed: " + passed + ", failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void checkBasicPutAndGet() {
        SimpleCache cache = new SimpleCache(3);
        cache.put("a", "1");
        cache.put("b", "2");
        check("basic put/get", "1".equals(cache.get("a")) && "2".equals(cache.get("b")));
    }

    private static void checkMissingKeyReturnsNull() {
        SimpleCache cache = new SimpleCache(3);
        check("missing key returns null", cache.get("nope") == null);
    }

    private static void checkOverwriteUpdatesValue() {
        SimpleCache cache = new SimpleCache(3);
        cache.put("a", "1");
        cache.put("a", "9");
        check("overwrite updates value", "9".equals(cache.get("a")) && cache.size() == 1);
    }

    private static void checkEvictionRemovesLeastRecentlyUsed() {
        SimpleCache cache = new SimpleCache(3);
        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");
        cache.put("d", "4"); // "a" was least recently used
        check("eviction removes least recently used",
                cache.get("a") == null && "2".equals(cache.get("b")) && cache.size() == 3);
    }

    private static void checkGetRefreshesRecency() {
        SimpleCache cache = new SimpleCache(3);
        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");
        cache.get("a"); // "a" is now most recently used; "b" is least
        cache.put("d", "4");
        check("get refreshes recency",
                "1".equals(cache.get("a")) && cache.get("b") == null && cache.size() == 3);
    }

    private static void checkPutOnExistingKeyRefreshesRecency() {
        SimpleCache cache = new SimpleCache(3);
        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");
        cache.put("a", "9"); // re-putting "a" should refresh its recency, like get() does
        cache.put("d", "4"); // correct victim is "b", not "a"
        check("put on existing key refreshes recency",
                "9".equals(cache.get("a")) && cache.get("b") == null && cache.size() == 3);
    }

    private static void checkCapacityOne() {
        SimpleCache cache = new SimpleCache(1);
        cache.put("a", "1");
        cache.put("b", "2");
        check("capacity 1 keeps only the newest",
                cache.get("a") == null && "2".equals(cache.get("b")) && cache.size() == 1);
    }

    private static void checkSizeAfterEviction() {
        SimpleCache cache = new SimpleCache(2);
        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");
        cache.put("d", "4");
        check("size stays at capacity after evictions", cache.size() == 2);
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("PASS: " + name);
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }
}
