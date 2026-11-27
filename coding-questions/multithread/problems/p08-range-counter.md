# P08 — Parallel Range Counter with ForkJoinPool

Medium. Practices `ForkJoinPool` + `RecursiveTask` — parallel divide-and-conquer:
split the range in half until it is small enough to count directly, then combine
the children's results.

## Pattern (memorize this shape)

Unlike P01/P02, there is no fixed set of workers. One task splits itself in half,
again and again, until each piece is small enough to handle directly. Memorize
this shape, then fill in the blanks.

```java
private static final class CountTask extends RecursiveTask<Long> {
    private final int[] nums;
    private final int from, to;                 // this task's half-open segment [from, to)
    private final int low, high;                // the closed interval to count in

    CountTask(int[] nums, int from, int to, int low, int high) { ... }

    @Override
    protected Long compute() {
        if (to - from <= THRESHOLD) {           // small enough: count directly
            long count = 0;
            for (int i = from; i < to; i++)
                if (nums[i] >= low && nums[i] <= high) count++;
            return count;
        }
        int mid = from + (to - from) / 2;
        CountTask left = new CountTask(nums, from, mid, low, high);
        CountTask right = new CountTask(nums, mid, to, low, high);
        left.fork();                            // run the left half asynchronously
        long rightCount = right.compute();      // work on the right half yourself
        return left.join() + rightCount;        // wait for the left half, add both
    }
}

ForkJoinPool pool = new ForkJoinPool();
try {
    return pool.invoke(new CountTask(nums, 0, nums.length, low, high));
} finally {
    pool.shutdown();                            // always, or the JVM never exits
}
```

- Each subtask returns its own partial count. No shared mutable state, no race.
- `fork()` + `join()` is the barrier: `join()` blocks until that half is done and
  hands you its result. `fork()` without `join()` loses half the count.
- Fork one half and `compute()` the other yourself: forking both halves and then
  joining both also works, but wastes the current thread — it would sit idle
  waiting instead of doing half the work.
- The threshold stops the splitting: without it, a task would split down to
  single elements and the fork/join overhead would dwarf the counting. Here the
  threshold is `10000` — a segment of at most 10000 elements is counted directly.
- `pool.invoke()` runs the root task and blocks until it is done; it also lets
  the calling thread help with the work instead of waiting idle.
- `shutdown()` goes in `finally`: if `invoke()` throws, the pool still shuts down.

## Three self-check questions (answer before you write)

1. **What state is shared between threads?** Here: nothing mutable — each task
   reads only its own segment of `nums` (never written) and returns its own
   `long` count. If your answer names a shared counter that subtasks `+=` into,
   stop: that is the race, and counts get lost.
2. **How does the main thread know all subtasks are done?** `pool.invoke()`
   returns only when the whole task tree is finished, and every parent `join()`s
   its forked child before adding the results. Returning without a `join()`
   gives half the count.
3. **What happens if a subtask throws, or if you forget `shutdown()`?** The
   exception surfaces at `invoke()` / `join()`; a pool that is never shut down
   keeps non-daemon threads alive and the JVM never exits — the tests check for
   leaked pool threads after the call returns.

## Statement

Given an integer array `nums` and a closed interval `[low, high]`, count how many
elements of `nums` fall in the interval, in parallel, using a `ForkJoinPool` and a
`RecursiveTask`. Split the range recursively in half until a segment's length is
at most the threshold (`10000`), then count that segment directly; a parent task
adds its two children's results. Return the total as a `long`.

## API

```java
public long countInRange(int[] nums, int low, int high)
```

## Examples

| nums | low | high | returns |
|---|---|---|---|
| `[1, 5, 3, 8, 2]` | 2 | 5 | `3` (the elements 5, 3, 2) |
| `[]` | 0 | 10 | `0` |
| `[7, 7, 7]` | 7 | 7 | `3` |
| `[1, 2, 3]` | 5 | 10 | `0` |

## Constraints

- `0 <= nums.length <= 10^7`
- `-10^9 <= nums[i] <= 10^9`
- `low <= high` — the interval is closed: both endpoints count

## Common pitfalls

- Having subtasks `+=` into one shared counter without synchronization — `+=` is
  not atomic, counts get lost. Each subtask must return its own partial result
  and the parent adds them.
- `fork()` without `join()` — the forked half runs (or maybe never runs), but its
  result is never collected, so you lose half the count.
- Splitting past the threshold down to single elements — the fork/join overhead
  dwarfs the counting. Stop splitting at `THRESHOLD` (`10000`) and count directly.
- Forgetting `pool.shutdown()` — pool threads are not daemon threads, so the
  program never exits. The tests check for leaked pool threads after the call
  returns.
- Splitting unevenly and dropping elements at the segment boundaries — the random
  datasets in the tests compare against a direct sequential count.

## How to run the tests

From `coding-questions/multithread/`:

```bash
./run-tests.sh          # macOS / Linux
```

```powershell
.\run-tests.ps1         # Windows PowerShell
```

Or open `coding-questions/multithread/` in VS Code and run the "All tests" launch
configuration. The P08 suite contributes 15 checks; run it alone with
`java -cp out AllTests "Range counter"` (prints `##RESULT 15 0` when green).
