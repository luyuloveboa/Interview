# P04 — BlockingQueue Task Pipeline

Medium. Practices `BlockingQueue` producer–consumer, with a poison pill to stop
the consumers.

## Pattern (memorize this shape)

One producer thread puts tasks into a bounded queue; N consumer threads take
tasks, process them, and collect the results. When the work is done, the
producer sends one poison pill per consumer; a consumer exits when it takes a
poison pill.

```java
int POISON = Integer.MIN_VALUE;                   // inputs never contain this value
BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(queueCapacity);
List<Integer> results = Collections.synchronizedList(new ArrayList<>());
ExecutorService pool = Executors.newFixedThreadPool(consumerCount);
try {
    List<Future<?>> futures = new ArrayList<>();
    for (int c = 0; c < consumerCount; c++) {
        futures.add(pool.submit(() -> {
            try {
                while (true) {
                    int task = queue.take();      // blocks until a task arrives
                    if (task == POISON) return;   // poison pill: this consumer exits
                    results.add(task * 2);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
    }
    for (int task : inputs) queue.put(task);      // blocks when the queue is full
    for (int c = 0; c < consumerCount; c++) queue.put(POISON);  // one pill PER consumer
    for (Future<?> f : futures) f.get();          // barrier: wait for every consumer
    return results;
} finally {
    pool.shutdown();                              // always, or the JVM never exits
}
```

- `ArrayBlockingQueue` is bounded: `put()` blocks when the queue is full, which
  is the back-pressure. An unbounded queue (`LinkedBlockingQueue` with no
  capacity) never blocks the producer and misses the point of the exercise.
- `take()` blocks until a task arrives. A consumer with nothing to do waits
  instead of spinning — but it only ever wakes up for a real task or a pill.
- One pill per consumer: each pill is taken by exactly one consumer, so N
  consumers need N pills. One pill exits one consumer; the rest stay stuck on
  `take()` forever.
- The results list is shared by all consumers, so it must be thread-safe
  (`Collections.synchronizedList`, or a `ConcurrentLinkedQueue`). A plain
  `ArrayList` loses or corrupts concurrent `add()`s.
- `Future.get()` is the barrier, as in P01: it blocks until that consumer has
  exited (after its pill), and hands you the consumer's exception if it threw.
- `shutdown()` goes in `finally`: if `get()` throws, the pool still shuts down.

## Three self-check questions (answer before you write)

1. **What state is shared between threads?** The queue — thread-safe by
   design — and the results list, which must be a synchronized list or a
   concurrent collection. If your answer names a plain `ArrayList` shared
   between consumers, stop: concurrent `add()`s race.
2. **How many poison pills, and who sends them?** One per consumer, sent by
   the producer (the main thread) after the last input is in. If your answer
   is "one pill", stop: only one of N consumers exits and the rest block
   forever on `take()`.
3. **What happens if you forget the pills, or if you forget `shutdown()`?**
   Without pills the consumers never exit, `get()` never returns, and the
   method hangs — the tests abort the check on a timeout. A pool that is
   never shut down keeps non-daemon threads alive and the JVM never exits —
   the tests check for leaked pool threads after the call returns.

## Statement

One producer thread puts tasks (integers) into a bounded `ArrayBlockingQueue`
(capacity from a parameter), N consumer threads take tasks, compute `task * 2`,
and collect the results. After all inputs are in, the producer sends one
poison pill per consumer; a consumer exits when it takes a poison pill. Return
all results (order free). The poison pill is `Integer.MIN_VALUE`; inputs never
contain it. You must use a bounded queue (`ArrayBlockingQueue`), not an
unbounded one.

## API

```java
public List<Integer> runPipeline(List<Integer> inputs, int queueCapacity, int consumerCount)
```

## Examples

| inputs | queueCapacity | consumerCount | returns |
|---|---|---|---|
| `[1, 2, 3]` | 10 | 2 | `[2, 4, 6]` |
| `[]` | 5 | 2 | `[]` |
| `[5]` | 1 | 1 | `[10]` |

Worked example: `[1, 2, 3]`, capacity 10, 2 consumers. The producer puts 1,
2, 3, then one poison pill per consumer (2 pills). Each consumer takes tasks
and doubles them: 1 → 2, 2 → 4, 3 → 6. Both consumers then take a pill and
exit. The returned list is `[2, 4, 6]` in some order — order is free, so the
tests compare sorted results.

## Constraints

- `0 <= inputs.size() <= 100000`
- `1 <= queueCapacity <= 1000`
- `1 <= consumerCount <= 16`
- inputs do not contain `Integer.MIN_VALUE`

## Common pitfalls

- Forgetting the poison pill — consumers block forever on `take()`, the method
  never returns (the test harness timeout FAILs it).
- Sending only one pill — only one of N consumers exits, the rest stay stuck.
- Using an unbounded queue — the producer never blocks, so the capacity
  parameter does nothing and the back-pressure the exercise practices is gone.
- Collecting results into a plain `ArrayList` — concurrent `add()`s from N
  consumers race and results get lost or corrupted.
- Returning before the consumers are done — `Future.get()` on every consumer
  future is the barrier; returning early hands back a partial list.

## How to run the tests

From `coding-questions/multithread/`:

```bash
./run-tests.sh          # macOS / Linux
```

```powershell
.\run-tests.ps1         # Windows PowerShell
```

Or open `coding-questions/multithread/` in VS Code and run the "All tests" launch
configuration. This suite contributes 10 checks; the full run passes when the
last line reads `ALL PASSED (N checks)` with no failures.
