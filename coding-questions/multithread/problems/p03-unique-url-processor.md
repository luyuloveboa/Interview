# P03 — Parallel Unique URL Processor

Medium. Practices atomic claim / test-and-set with a `ConcurrentHashMap`
(`putIfAbsent`, or `newKeySet().add()`).

## Pattern (memorize this shape)

One task per URL, submitted to a fixed pool. Each task claims its URL with a
single atomic operation — the claim *is* the test. Only the thread whose claim
succeeds processes the URL.

```java
Set<String> claimed = ConcurrentHashMap.newKeySet();   // the atomic claim set
List<String> processed = Collections.synchronizedList(new ArrayList<>());
ExecutorService pool = Executors.newFixedThreadPool(threadCount);
try {
    List<Future<?>> futures = new ArrayList<>();
    for (String url : urls) {
        futures.add(pool.submit(() -> {
            if (claimed.add(url)) {            // ONE atomic step: claim + test
                Thread.sleep(1);               // simulated network request, 1 ms
                processed.add(url);            // only the claiming thread gets here
            }
        }));
    }
    for (Future<?> f : futures) f.get();       // barrier: wait for every task
    return processed;
} finally {
    pool.shutdown();                           // always, or the JVM never exits
}
```

- `claimed.add(url)` returns `true` for exactly one thread — the first one.
  That single call is both the test ("has anyone claimed this URL?") and the
  claim ("it's mine now"). There is no gap for another thread to slip through.
  The equivalent with a map is `claimed.putIfAbsent(url, Boolean.TRUE) == null`.
- `Thread.sleep(1)` runs only after a successful claim, so a repeated URL is
  never processed twice and never even pays the 1 ms. It is part of the spec —
  don't drop it: it simulates the network request and widens the race window
  that catches the two-step bug.
- `processed` is shared across threads, so it must be thread-safe
  (`Collections.synchronizedList`, or a concurrent collection). The result
  order is free — the tests sort before comparing.
- One task per URL is fine even at the 100,000 limit: the pool bounds the
  threads, the queue holds the tasks.
- `Future.get()` is the barrier: return before it and you hand back a partial
  list. `shutdown()` goes in `finally`: if `get()` throws, the pool still
  shuts down.
- `Thread.sleep(1)` throws `InterruptedException`, so the task body needs a
  `try`/`catch` around it; restore the interrupt flag if you catch it.

## Three self-check questions (answer before you write)

1. **What state is shared between threads?** Here: the `claimed` set and the
   `processed` list. If your answer names a plain `HashSet` or a plain
   `ArrayList`, stop: neither is thread-safe, and concurrent use corrupts
   them even if the claim logic is right.
2. **How do you know each distinct URL is processed exactly once?**
   `claimed.add(url)` returns `true` for exactly one thread. If your answer
   is "check `contains`, then `add`" — that is two steps, and two threads can
   both pass the check before either adds. That is the bug this exercise is
   about.
3. **What happens if you forget `shutdown()`, or return before `get()`?** A
   pool that is never shut down keeps non-daemon threads alive and the JVM
   never exits — the tests check for leaked pool threads after the call
   returns. Returning before `get()` hands back a partial list.

## Statement

Given a list of URLs `urls` (which may contain duplicates) and a thread count
`threadCount`, process each *distinct* URL exactly once, in parallel, and
return the list of distinct processed URLs (order free). "Processing" is
simulated with `Thread.sleep(1)` (1 millisecond) per URL — it simulates a
network request and also widens the race window, so do not omit it. A repeated
URL is processed only at its first occurrence: the thread that claims it first
processes it, and every later occurrence is skipped.

## API

```java
public List<String> processUnique(List<String> urls, int threadCount)
```

## Examples

| urls | threadCount | returns |
|---|---|---|
| `["a", "b", "a", "c", "b"]` | 2 | `["a", "b", "c"]` |
| `[]` | 4 | `[]` |
| `["x", "x", "x"]` | 2 | `["x"]` |

Worked example: `["a", "b", "a", "c", "b"]` with `k = 2` — the distinct URLs
are `a`, `b`, `c`. The second `a` and the second `b` are repeats, so they are
claimed already and skipped; each of `a`, `b`, `c` is slept on once and appears
once in the result, in any order.

## Constraints

- `0 <= urls.size() <= 100000`
- each URL is a non-empty string
- `1 <= threadCount <= 16`

## Common pitfalls

- `if (!seen.contains(u)) { seen.add(u); ... }` is two steps — two threads
  pass the check and both process the same URL. The claim must be one atomic
  operation: `ConcurrentHashMap.newKeySet().add(u)` or `putIfAbsent`.
- Using a plain `HashSet` / `HashMap` for the claim set — not thread-safe,
  concurrent access corrupts it. Use `ConcurrentHashMap.newKeySet()` (or a
  `ConcurrentHashMap` with `putIfAbsent`).
- Using a plain `ArrayList` for the shared result — concurrent `add` loses
  entries. Use a synchronized or concurrent list.
- Sleeping *before* claiming (or sleeping for every occurrence, claimed or
  not) — duplicates pay the cost and race the claim. Sleep only after a
  successful claim.
- Returning before `Future.get()` — you hand back a partial list, missing
  URLs whose tasks haven't finished yet.
- Forgetting `pool.shutdown()` — pool threads are not daemon threads, so the
  program never exits. The tests check for leaked pool threads after the call
  returns.
- Dropping the `Thread.sleep(1)` — it's part of the spec (simulated slow I/O);
  without it, the two-step claim bug is much harder to catch.

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
