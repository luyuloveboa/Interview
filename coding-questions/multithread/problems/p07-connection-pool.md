# P07 — Connection Pool with Semaphore

Medium. Practices `Semaphore` for bounded access + explicit tracking of which
resources are checked out (a bare `Semaphore` is NOT enough).

## Pattern (memorize this shape)

A pool is two pieces of state that must agree: a `Semaphore` that bounds how many
borrowers there can be at once, and a record of *which* connections are out.
The semaphore alone cannot tell a real return from a double release.

```java
Semaphore permits = new Semaphore(size, true);  // at most size borrowers at once
boolean[] checkedOut = new boolean[size];        // which ids are currently lent out
Object lock = new Object();                      // guards checkedOut

// acquire(): take a permit first (blocks when the pool is empty), then claim a free id
permits.acquire();                               // throws InterruptedException
synchronized (lock) {
    for (int i = 0; i < size; i++) {
        if (!checkedOut[i]) {
            checkedOut[i] = true;
            return "conn-" + i;
        }
    }
}
// (unreachable when permits and checkedOut agree — a permit was taken, so an id is free)

// release(): consult checkedOut BEFORE giving the permit back
synchronized (lock) {
    if (id is not a checked-out "conn-i") return;   // no-op: double release / unknown id
    checkedOut[i] = false;
}
permits.release();
```

- `acquire()` takes the permit *before* claiming an id: the permit is what makes
  the caller wait when the pool is empty. Claiming the id happens under `lock`,
  so two threads can never be handed the same id.
- `release()` checks `checkedOut` first and returns the permit only for an id
  that is actually out. A double release or an unknown id is a no-op — it must
  never mint a new permit.
- Invariant: `permits.availablePermits() + (number of checkedOut entries) == size`
  at every moment. Every path that takes a permit marks an id; every path that
  returns a permit unmarks one.
- `acquire()` declares `throws InterruptedException`: let the interrupt propagate
  instead of swallowing it — and never hand out a connection you did not claim.

## Three self-check questions (answer before you write)

1. **What state is shared between threads?** The `Semaphore` (thread-safe on its
   own) and the `checkedOut` array (only ever touched under `lock`). If your
   answer is "just the semaphore", stop: that is the bug this exercise is about.
2. **What stops the pool lending out more than N connections?** The permit count
   *and* the checked-out set agreeing. A bare semaphore counts returns, not
   connections — a double release adds a permit for a connection that was never
   returned.
3. **What happens if `release()` is called twice, with an id that was never
   borrowed, or if `acquire()` is interrupted?** The first two must be silent
   no-ops; the interrupt must propagate out of `acquire()` (restore the flag
   only if you catch it — better to declare `throws` and not catch it at all).

## Statement

Implement a pool of `N` database connections, with ids `"conn-0"` ...
`"conn-(N-1)"`. `acquire()` blocks when all connections are in use and returns a
free connection's id; `release(id)` returns it to the pool. Releasing a
connection that is NOT currently checked out (a double release, or an id that
was never borrowed) MUST be a no-op and must NOT create an extra permit — a
double release must never let the pool lend out more than `N` connections at
once.

## API

```java
public ConnectionPool(int size)
public String acquire() throws InterruptedException
public void release(String connectionId)
```

## Examples

| size | calls | result |
|---|---|---|
| 2 | `acquire()`, `acquire()`, `release("conn-0")`, `acquire()` | the first two are two different ids; the third `acquire()` gets `"conn-0"` again (reuse) |
| 1 | `acquire()`, then a second `acquire()` | the second blocks until someone `release()`s |
| 1 | `acquire()` → `"conn-0"`, `release("conn-0")` twice | afterwards only ONE `acquire()` can succeed immediately; the second must block (a double release must not create an extra permit) |

## Constraints

- `1 <= size <= 100`

## Common pitfalls

- A bare `Semaphore` only (`acquire()` takes a permit, `release()` gives one
  back) — a double release adds a permit and the pool lends out more
  connections than actually exist. Keep a separate "which connections are
  checked out" state and consult it on `release()`.
- Giving the permit back *before* checking `checkedOut` (or without checking at
  all) — same bug, just ordered differently: the permit count and the
  checked-out set drift apart.
- Handing out ids without tracking them (for example, a round-robin counter) —
  two threads can be handed the same id at once, and a released id is never
  recognizably reused.
- Swallowing `InterruptedException` in `acquire()` — a thread interrupted while
  waiting must not come back holding (or not holding) a connection it never
  claimed; declare `throws InterruptedException` and let it propagate.
- Releasing an id that was never borrowed (or `null`) throwing or minting a
  permit — it must be a silent no-op.

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
(10 for P01, 10 for P02, 11 for P03, 10 for P04, 12 for P05, 10 for P06, 10 for P07, 15 for P08, 14 for P09). To run just this exercise (after
compiling with `./run-tests.sh`):

```bash
java -cp out AllTests "Connection pool"
```
