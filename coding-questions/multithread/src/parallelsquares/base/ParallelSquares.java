package parallelsquares.base;

import java.util.*;
import java.util.concurrent.*;

/**
 * P02 — Parallel Squares with CountDownLatch (Easy).
 *
 * Compute the square of every element in parallel, returning the results
 * in the same order as the input. Each worker handles one contiguous slice
 * and writes only its own segment of the result array. Each element takes
 * a simulated slow step: Thread.sleep(5) before squaring.
 * A CountDownLatch (count = workerCount) signals completion: each worker
 * counts down once in finally, and the main thread awaits it before returning.
 *
 * Fill in parallelSquares() below, then run the tests from
 * coding-questions/multithread/:
 *   ./run-tests.sh          (macOS / Linux)
 *   .\run-tests.ps1         (Windows PowerShell)
 *
 * Note: if all tests print but the program never exits, you forgot
 * pool.shutdown() — pool threads are not daemon threads.
 */
public class ParallelSquares {

    public int[] parallelSquares(int[] nums, int workerCount) {
        // TODO: implement this method
        throw new UnsupportedOperationException("TODO");
    }
}
