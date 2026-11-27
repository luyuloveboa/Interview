package uniqueurl.base;

import java.util.*;
import java.util.concurrent.*;

/**
 * P03 — Parallel Unique URL Processor (Medium).
 *
 * Given a list of URLs (which may contain duplicates) and a thread count,
 * process each DISTINCT URL exactly once, in parallel, and return the list
 * of distinct processed URLs (order free). "Processing" is simulated with
 * Thread.sleep(1) (1 millisecond) per URL. A repeated URL is processed only
 * at its first occurrence.
 *
 * The claim must be one atomic operation: ConcurrentHashMap.newKeySet().add(url)
 * (or putIfAbsent) returns true for exactly one thread. The two-step
 * if (!seen.contains(u)) { seen.add(u); ... } lets two threads through.
 *
 * Fill in processUnique() below, then run the tests from
 * coding-questions/multithread/:
 *   ./run-tests.sh          (macOS / Linux)
 *   .\run-tests.ps1         (Windows PowerShell)
 *
 * Note: if all tests print but the program never exits, you forgot
 * pool.shutdown() — pool threads are not daemon threads.
 */
public class UniqueUrlProcessor {

    public List<String> processUnique(List<String> urls, int threadCount) {
        // TODO: implement this method
        throw new UnsupportedOperationException("TODO");
    }
}
