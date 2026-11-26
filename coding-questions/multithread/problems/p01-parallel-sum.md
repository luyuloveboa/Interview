# P01 — Parallel Array Sum

Easy. Practices `ExecutorService` + `Future`, with `Future.get()` as the barrier.

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
configuration. All tests pass when the last line is `ALL PASSED (10 checks)`.
