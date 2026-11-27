package parallelsum.base;

import java.util.*;
import java.util.concurrent.*;

/**
 * P01 — Parallel Array Sum (Easy).
 *
 * Split nums into threadCount contiguous segments (as evenly as possible),
 * let one worker sum each segment, and collect the partial sums with
 * Future.get() in the main thread. Return the total as a long.
 *
 * Fill in parallelSum() below, then run the tests from
 * coding-questions/multithread/:
 *   ./run-tests.sh          (macOS / Linux)
 *   .\run-tests.ps1         (Windows PowerShell)
 *
 * Note: if all tests print but the program never exits, you forgot
 * pool.shutdown() — pool threads are not daemon threads.
 */
public class ParallelSum {

    public long parallelSum(int[] nums, int threadCount) {
        // TODO: implement this method
        throw new UnsupportedOperationException("TODO");
    }
}
