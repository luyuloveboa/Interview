package taskpipeline.base;

import java.util.*;

/**
 * P04 — BlockingQueue Task Pipeline (Medium).
 *
 * One producer thread puts tasks (integers) into a bounded ArrayBlockingQueue
 * (capacity from a parameter), consumerCount consumer threads take tasks,
 * compute task * 2, and collect the results. After all inputs are in, the
 * producer sends ONE poison pill PER consumer; a consumer exits when it takes
 * a poison pill. Return all results (order free).
 *
 * The poison pill is Integer.MIN_VALUE; inputs never contain it. You must use
 * a bounded queue (ArrayBlockingQueue), not an unbounded one.
 *
 * Fill in runPipeline() below, then run the tests from
 * coding-questions/multithread/:
 *   ./run-tests.sh          (macOS / Linux)
 *   .\run-tests.ps1         (Windows PowerShell)
 *
 * Note: if the tests print but the program never exits, you forgot
 * pool.shutdown() — pool threads are not daemon threads. And if a check
 * times out, you probably forgot the poison pills — consumers block forever
 * on take().
 */
public class TaskPipeline {

    public List<Integer> runPipeline(List<Integer> inputs, int queueCapacity, int consumerCount) {
        // TODO: implement this method
        throw new UnsupportedOperationException("TODO");
    }
}
