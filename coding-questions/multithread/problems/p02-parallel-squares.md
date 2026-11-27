# P02 — Parallel Squares with CountDownLatch

Easy. Practices `CountDownLatch` — fixed-count coordination: workers count down when
they finish, the main thread awaits.

## Pattern (memorize this shape)

Same fixed-task-set shape as P01, but the workers write into a shared result array
instead of returning values, so the barrier is a `CountDownLatch` instead of
`Future.get()`.

```java
int[] result = new int[nums.length];
CountDownLatch latch = new CountDownLatch(workerCount);
ExecutorService pool = Executors.newFixedThreadPool(workerCount);
try {
    // split nums into workerCount contiguous segments (as evenly as possible)
    for (int w = 0; w < workerCount; w++) {
        int from = ..., to = ...;                 // this worker's segment
        pool.submit(() -> {
            try {
                for (int i = from; i < to; i++) {
                    Thread.sleep(5);              // simulated slow I/O, 5 ms per element
                    result[i] = nums[i] * nums[i];
                }
            } finally {
                latch.countDown();                // always, even if this worker threw
            }
        });
    }
    latch.await();                                // barrier: wait for every worker
    return result;
} finally {
    pool.shutdown();                              // always, or the JVM never exits
}
```

- Each worker writes only its own segment of `result`. The writes never overlap, so
  the writes themselves have no race.
- `latch.await()` is the barrier: it blocks until the count hits zero. Return before
  it and you hand back an unfilled array.
- `countDown()` goes in `finally`: if a worker throws, the latch still reaches zero.
  Otherwise the main thread blocks forever.
- Split evenly: `base = n / workerCount`, `rem = n % workerCount`, and the first `rem`
  segments get one extra element. Segment sizes differ by at most 1.
- `Thread.sleep(5)` is per element and is part of the spec — don't drop it to make
  the tests run faster. It throws `InterruptedException`, so the worker body needs
  a `try`/`catch` around it; restore the interrupt flag if you catch it.
- `shutdown()` goes in `finally`: if `await()` throws, the pool still shuts down.

## Three self-check questions (answer before you write)

1. **What state is shared between threads?** Here: the `result` array — but each
   worker writes only its own segment, and the main thread only reads it after
   `await()`. If your answer names a shared counter or a shared "next index",
   stop: that is the race.
2. **How does the main thread know all workers are done?** `latch.await()`.
   Returning before that gives an unfilled array (zeros where the workers
   haven't written yet).
3. **What happens if a worker throws, or if you forget `shutdown()`?** The
   exception must not skip `countDown()` — that's why it's in `finally`;
   a pool that is never shut down keeps non-daemon threads alive and the JVM
   never exits — the tests check for leaked pool threads after the call returns.

## Statement

Given an integer array `nums` and a worker count `workerCount`, compute the square
of every element in parallel and return the results in the same order as the input.
Split the array into `workerCount` contiguous segments (as evenly as possible —
segment sizes differ by at most 1), let each worker handle one segment and write
only its own segment of the result array. Each element's processing simulates
slow I/O: `Thread.sleep(5)` (5 milliseconds) before squaring. Use one
`CountDownLatch` (count = `workerCount`) as the completion signal: each worker
counts down once when its segment is done (in `finally`), and the main thread
`await()`s it before returning.

## API

```java
public int[] parallelSquares(int[] nums, int workerCount)
```

## Examples

| nums | workerCount | returns |
|---|---|---|
| `[1, 2, 3, 4]` | 2 | `[1, 4, 9, 16]` |
| `[0, -3, 5]` | 1 | `[0, 9, 25]` |
| `[]` | 4 | `[]` |
| `[5, 7]` | 10 | `[25, 49]` |
| `[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]` | 3 | `[1, 4, 9, 16, 25, 36, 49, 64, 81, 100]` |

## Constraints

- `0 <= nums.length <= 10^4`
- `-10^4 <= nums[i] <= 10^4` — the square fits in an `int` (`10000 * 10000 = 100000000`)
- `1 <= workerCount <= 16` — `workerCount` may be larger than the array (empty segments are fine)

## Common pitfalls

- Returning before `latch.await()` — you hand back an unfilled array, zeros where
  the workers haven't written yet.
- `countDown()` not in `finally` — a worker that throws leaves the latch short,
  and the main thread blocks forever.
- Letting workers write overlapping segments (for example, every worker writing
  from index 0) — the writes race and the results are wrong. Each worker writes
  only its own segment.
- Forgetting `pool.shutdown()` — pool threads are not daemon threads, so the program
  never exits. The tests check for leaked pool threads after the call returns.
- Splitting unevenly and dropping elements at the segment boundaries — the random
  datasets in the tests compare against a direct sequential computation.
- Dropping the `Thread.sleep(5)` — it's part of the spec (simulated slow I/O);
  without it, the "forgot await" bug is much harder to catch.

## How to run the tests

From `coding-questions/multithread/`:

```bash
./run-tests.sh          # macOS / Linux
```

```powershell
.\run-tests.ps1         # Windows PowerShell
```

Or open `coding-questions/multithread/` in VS Code and run the "All tests" launch
configuration. All tests pass when the last line is `ALL PASSED (20 checks)`
(10 for P01, 10 for P02).
