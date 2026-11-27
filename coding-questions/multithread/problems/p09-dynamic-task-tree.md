# P09 — Dynamic Task Tree with Phaser

Medium. Practices `Phaser` — termination when the task count grows dynamically:
register BEFORE submit; `arriveAndDeregister` in `finally`. A `CountDownLatch`
cannot work here: the total number of tasks is only known when the traversal
is done.

## Pattern (memorize this shape)

Every dynamic-task-count parallel problem follows the same shape. Memorize it,
then fill in the blanks.

```java
// shared state (fields of the class, set up fresh in traverse())
Set<Integer> visited = ConcurrentHashMap.newKeySet();  // atomic claim
AtomicInteger processed = new AtomicInteger();         // atomic counter
Phaser phaser = new Phaser(1);                         // main thread is party 1
ExecutorService pool = Executors.newFixedThreadPool(threadCount);

try {
    visited.add(start);
    phaser.register();                                 // register BEFORE submit
    pool.submit(() -> process(start));
    phaser.arriveAndAwaitAdvance();                    // main waits for phase 0 to end
    processedCount = processed.get();                  // publish the count at the end
    return new HashSet<>(visited);
} finally {
    pool.shutdown();                                   // always, or the JVM never exits
}

void process(int node) {
    try {
        Thread.sleep(1);                               // simulated work, 1 ms per node
        processed.incrementAndGet();
        for (int next : graph.getOrDefault(node, List.of())) {
            if (visited.add(next)) {                   // atomic claim — the ONLY place a node is claimed
                phaser.register();                     // register BEFORE submit
                pool.submit(() -> process(next));
            }
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    } finally {
        phaser.arriveAndDeregister();                  // always, even if this task threw
    }
}
```

- The `Phaser` starts with 1 party: the main thread. Every task registers
  itself (as one more party) **before** it is submitted, and deregisters in
  `finally` when it is done. The phase advances — and the main thread wakes up —
  only when every registered party has arrived, so tasks created late still
  hold the phase open.
- `visited.add(next)` is the claim, and it is atomic: it returns `false` if
  another thread already claimed that node. That single call is what stops a
  diamond or a cycle from reprocessing a node forever.
- `processedCount` is shared mutable state, so a plain `processedCount++` from
  worker threads can lose updates. Count with an `AtomicInteger` and publish
  it into `processedCount` before returning (or synchronize the increment).
- `graph.getOrDefault(node, List.of())`: a node with no key in the map is a
  leaf, not an error.
- `arriveAndDeregister()` goes in `finally`: if a task throws, the Phaser
  still advances. Otherwise the main thread blocks forever.
- `shutdown()` goes in `finally`: if `awaitAdvance()` throws, the pool still
  shuts down.

## Three self-check questions (answer before you write)

1. **What state is shared between threads?** Here: the `visited` set and the
   processed counter. The claim must be one atomic step — `visited.add(next)`
   returning `false` for an already-claimed node. If your answer is
   "check `contains`, then `add`" as two steps, stop: that is the race — two
   threads can both pass the check, both submit the node, and on a cycle the
   program creates new tasks forever.
2. **How does the main thread know all tasks are done?** The `Phaser` phase
   advancing, via `arriveAndAwaitAdvance()`. A `CountDownLatch` cannot work
   here because the total is only known when the traversal is done, and
   `join()` polling is not a termination signal. Registering after `submit()`
   lets the phase advance early and wakes the main thread with an incomplete
   set.
3. **What happens if a worker throws, or if you forget `shutdown()`?** The
   exception must not skip `arriveAndDeregister()` — that's why it's in
   `finally`; a pool that is never shut down keeps non-daemon threads alive
   and the JVM never exits — the tests check for leaked pool threads after
   the call returns.

## Statement

Given a directed graph (adjacency list; `graph.get(u)` is `u`'s out-edges) and
a start node, use `threadCount` worker threads to traverse all nodes reachable
from `start` in parallel, and return the reachable set. Each node is processed
exactly once (nodes rediscovered in a diamond or a cycle must NOT be
reprocessed); "processing" is simulated with `Thread.sleep(1)` per node (1 ms,
do not omit). MUST use a `Phaser` to decide when all tasks are done — NOT
`CountDownLatch`, no `join()` polling. The class has a `processedCount` field,
reset to zero at the start of each `traverse`, incremented by 1 per node
processed (tests use it to verify each node is processed exactly once).

## API

```java
public Set<Integer> traverse(Map<Integer, List<Integer>> graph, int start, int threadCount)
```

Plus a `public int processedCount` field (reset at the start of `traverse`).

## Examples

| graph | start | returns |
|---|---|---|
| `0->[1], 1->[2], 2->[3]` | `0` | `{0, 1, 2, 3}` |
| `0->[1, 2], 1->[3], 2->[3]` (diamond) | `0` | `{0, 1, 2, 3}`, 4 nodes processed exactly once |
| `0->[1], 1->[0, 2]` (cycle) | `0` | `{0, 1, 2}` — and the traversal MUST terminate |
| start not a key of graph | `9` | `{9}` |

Worked example (diamond): `0->[1, 2], 1->[3], 2->[3]`, start `0`. Node `0`
is claimed first, then its neighbours `1` and `2` are claimed and processed in
parallel. Both discover `3`, but only the first `visited.add(3)` returns
`true`, so `3` is submitted once and processed once: 4 nodes, `processedCount`
4, result `{0, 1, 2, 3}`.

Worked example (cycle): `0->[1], 1->[0, 2]`, start `0`. Node `1` rediscovers
`0`, but `0` is already in `visited`, so no new task is created for it; `2`
is claimed once and has no out-edges. The Phaser's last party deregisters,
the phase advances, and `traverse` returns `{0, 1, 2}` with `processedCount` 3
instead of looping forever.

## Constraints

- `0 <= n <= 10^4` (node count)
- `1 <= threadCount <= 16`
- The graph may contain cycles and duplicate edges
- `start` may not be a key of graph (treat it as a leaf)

## Common pitfalls

- `register()` written AFTER `submit()` — the phase can advance early and the
  main thread wakes up with an incomplete set.
- No `arriveAndDeregister()` in `finally` — after an exception the Phaser
  waits forever and the main thread blocks permanently.
- The claim is not atomic (the P03 lesson) — on a cycle, new tasks are created
  forever and the program never stops. `contains` followed by `add` is two
  steps, not one claim.
- Forgetting `shutdown()` — the program never exits. The tests check for
  leaked pool threads after the call returns.

## How to run the tests

From `coding-questions/multithread/`:

```bash
./run-tests.sh          # macOS / Linux
```

```powershell
.\run-tests.ps1         # Windows PowerShell
```

Or open `coding-questions/multithread/` in VS Code and run the "All tests" launch
configuration. All tests pass when the last line is `ALL PASSED (102 checks)`
(10 for P01, 10 for P02, 11 for P03, 10 for P04, 12 for P05, 10 for P06, 10 for P07, 15 for P08, 14 for P09).
