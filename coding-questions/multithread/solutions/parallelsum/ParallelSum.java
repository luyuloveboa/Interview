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
        try (ForkJoinPool pool = new ForkJoinPool(threadCount)) {
            return pool.submit(() -> Arrays.stream(nums).parallel().asLongStream().sum()).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Task execution failed", e);
        }
    }
    
    public long parallelSum1(int[] nums, int threadCount) {
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        try {
            List<Future<Long>> futures = new ArrayList<>();
            for (Tasks t : new Tasks(nums, threadCount)) {
                futures.add(pool.submit(t));
            }
            List<Long> partialSums = new ArrayList<>();
            for (Future<Long> f : futures) {
                try {
                    partialSums.add(f.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread interrupted", e);
                } catch (ExecutionException e) {
                    throw new RuntimeException("Task execution failed", e);
                }
            }
            return partialSums.stream().mapToLong(Long::longValue).sum();
        } finally {
            pool.shutdown();
        }
    }

    class Tasks implements Iterable<Tasks>, Callable<Long> {
        private final int[] nums;
        private final int from;
        private final int to;
        private final int threadCount;

        Tasks(int[] nums, int threadCount) {
            this.nums = nums;
            this.threadCount = threadCount;
            this.from = 0;
            this.to = nums.length;
        }

        private Tasks(int[] nums, int from, int to, int threadCount) {
            this.nums = nums;
            this.from = from;
            this.to = to;
            this.threadCount = threadCount;
        }

        @Override
        public Iterator<Tasks> iterator() {
            int n = nums.length;
            int base = n / threadCount;
            int remainder = n % threadCount;
            List<Tasks> tasks = new ArrayList<>();
            for (int i=0; i<threadCount; i++) {
                int start = i * base + Math.min(i, remainder);
                int end = start + base + (i < remainder ? 1 : 0);
                tasks.add(new Tasks(nums, start, end, threadCount));
            }
            return tasks.iterator();
        }

        @Override
        public Long call() {
            long sum = 0;
            for (int i = from; i < to; i++) {
                sum += nums[i];
            }
            return sum;
        }
    }
}
