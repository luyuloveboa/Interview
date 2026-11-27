# P01 — Parallel Array Sum

Easy. Practices `ExecutorService` + `Future`, with `Future.get()` as the barrier.

## Pattern (memorize this shape)

Every fixed-task-set parallel problem follows the same shape. Memorize it, then fill in
the blanks.

```java
ExecutorService pool = Executors.newFixedThreadPool(threadCount);
try {
    List<Future<Long>> futures = new ArrayList<>();
    // split nums into threadCount contiguous segments (as evenly as possible)
    for (int w = 0; w < threadCount; w++) {
        int from = ..., to = ...;                 // this worker's segment
        futures.add(pool.submit(() -> {
            long partial = 0;
            for (int i = from; i < to; i++) partial += nums[i];
            return partial;                       // each worker returns its own sum
        }));
    }
    long total = 0;
    for (Future<Long> f : futures) total += f.get();   // barrier: wait for every worker
    return total;
} finally {
    pool.shutdown();                              // always, or the JVM never exits
}
```

- Each worker writes only its own local variable. No shared mutable state, no race.
- `Future.get()` is the barrier: it blocks until that worker is done, and hands you the
  worker's exception if it threw.
- `shutdown()` goes in `finally`: if `get()` throws, the pool still shuts down.
- Split evenly: `base = n / threadCount`, `rem = n % threadCount`, and the first `rem`
  segments get one extra element. Segment sizes differ by at most 1.
- The total must be a `long`: `int` overflows (two `2000000000`s already exceed it).

## Three self-check questions (answer before you write)

1. **What state is shared between threads?** Here: nothing mutable — each worker has its
   own `partial` and its own segment. If your answer names a shared `long`, stop: that
   is the race.
2. **How does the main thread know all workers are done?** `Future.get()` on every
   future. Returning before that gives a partial sum.
3. **What happens if a worker throws, or if you forget `shutdown()`?** The exception
   surfaces at `get()`; a pool that is never shut down keeps non-daemon threads alive
   and the JVM never exits — the tests check for leaked pool threads after the call
   returns.

## Statement

Given an integer array `nums` and a thread count `threadCount`, sum the array in parallel
with `threadCount` worker threads. Split the array into `threadCount` contiguous segments
(as evenly as possible — segment sizes differ by at most 1), let each worker sum only its
own segment, and collect the partial sums in the main thread with `Future.get()`. Return
the total as a `long`.

## API

```java
public long parallelSum(int[] nums, int threadCount)
```

## Examples

| nums | threadCount | returns |
|---|---|---|
| `[1, 2, 3, 4, 5]` | 2 | `15` |
| `[-1, -2, 3]` | 3 | `0` |
| `[]` | 4 | `0` |
| `[7, 8]` | 1 | `15` |
| `[2000000000, 2000000000]` | 2 | `4000000000` |
| `[5, 7]` | 10 | `12` |

## Constraints

- `0 <= nums.length <= 10^6`
- `-10^9 <= nums[i] <= 10^9` — the total can exceed the `int` range, so it must be a `long`
- `1 <= threadCount <= 100` — `threadCount` may be larger than the array (empty segments are fine)

## Common pitfalls

- Returning before `Future.get()` — you only get a partial sum.
- Having all workers `+=` into one shared `long` — `+=` is not atomic, updates get lost.
  Each worker should sum its own segment into a local variable and return it.
- Forgetting `pool.shutdown()` — pool threads are not daemon threads, so the program
  never exits. The tests check for leaked pool threads after the call returns.
- Splitting unevenly and dropping elements at the segment boundaries — the random
  datasets in the tests compare against a direct sequential sum.

## How to run the tests

From `coding-questions/multithread/`:

```bash
./run-tests.sh          # macOS / Linux
```

```powershell
.\run-tests.ps1         # Windows PowerShell
```

Or open `coding-questions/multithread/` in VS Code and run the "All tests" launch
configuration. All tests pass when the last line is `ALL PASSED (102 checks)`.
