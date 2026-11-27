package tasktree.base;

import java.util.*;

/**
 * P09 — Dynamic Task Tree with Phaser (Medium).
 *
 * Traverse all nodes reachable from start in parallel with threadCount
 * worker threads, and return the reachable set. Each node is processed
 * exactly once (nodes rediscovered in a diamond or a cycle must NOT be
 * reprocessed); processing is simulated with Thread.sleep(1) per node.
 * MUST use a Phaser to decide when all tasks are done — NOT CountDownLatch,
 * no join() polling. processedCount is reset to zero at the start of each
 * traverse and incremented by 1 per node processed.
 *
 * Fill in traverse() below, then run the tests from
 * coding-questions/multithread/:
 *   ./run-tests.sh          (macOS / Linux)
 *   .\run-tests.ps1         (Windows PowerShell)
 *
 * Note: if all tests print but the program never exits, you forgot
 * pool.shutdown() — pool threads are not daemon threads.
 */
public class TaskTree {

    public int processedCount;

    public Set<Integer> traverse(Map<Integer, List<Integer>> graph, int start, int threadCount) {
        // TODO: implement this method
        throw new UnsupportedOperationException("TODO");
    }
}
