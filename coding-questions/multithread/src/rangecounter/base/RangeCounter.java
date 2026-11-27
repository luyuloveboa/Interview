package rangecounter.base;

/**
 * P08 — Parallel Range Counter with ForkJoinPool (Medium).
 *
 * Count how many elements of nums fall in the closed interval [low, high],
 * in parallel, with a ForkJoinPool and a RecursiveTask. Split the range
 * recursively in half until a segment's length is at most THRESHOLD, then
 * count that segment directly; a parent task adds its two children's results.
 * Return the total as a long.
 *
 * Fill in countInRange() below, then run the tests from
 * coding-questions/multithread/:
 *   ./run-tests.sh          (macOS / Linux)
 *   .\run-tests.ps1         (Windows PowerShell)
 *
 * Note: if all tests print but the program never exits, you forgot
 * pool.shutdown() — pool threads are not daemon threads.
 */
public class RangeCounter {

    /** Split a segment in half until it is at most this long, then count directly. */
    public static final int THRESHOLD = 10000;

    public long countInRange(int[] nums, int low, int high) {
        // TODO: implement this method
        throw new UnsupportedOperationException("TODO");
    }
}
