# P06 — BoundedBuffer with wait/notify

Hard. Practices `synchronized` + `wait()` / `notifyAll()` — the guarded block:
a thread waits until the condition it needs is true, and every state change wakes
everyone so the right kind of waiter can proceed.

> `BlockingQueue` is **forbidden** here — that is P04's approach. This problem
> wants the blocking logic written by hand, with `wait()` / `notifyAll()` (or
> `ReentrantLock` + `Condition`).

## Pattern (memorize this shape)

Every hand-written blocking structure follows the same shape. Memorize it, then
fill in the blanks.

```java
private final Object[] items;   // circular array, capacity slots
private int head, tail, count;  // all guarded by this object's monitor

public synchronized void put(T item) throws InterruptedException {
    while (count == items.length) wait();   // guard with while, NOT if
    items[tail] = item;
    tail = (tail + 1) % items.length;
    count++;
    notifyAll();                            // wake takers (and putters)
}

public synchronized T take() throws InterruptedException {
    while (count == 0) wait();              // guard with while, NOT if
    @SuppressWarnings("unchecked")
    T item = (T) items[head];
    items[head] = null;                     // don't hold the reference
    head = (head + 1) % items.length;
    count--;
    notifyAll();                            // wake putters (and takers)
    return item;
}

public synchronized int size() {
    return count;
}
```

- The condition is always re-checked in a `while` loop: `wait()` can return
  without a notification (spurious wakeup), and by the time a woken thread
  re-acquires the lock another thread may already have taken the slot (or the
  item) it was waiting for. An `if` lets it proceed on a false condition.
- `notifyAll()` wakes every waiter, because producers and consumers wait on
  the *same* monitor. `notify()` wakes one arbitrary waiter — it may wake a
  producer when only a consumer can make progress (or the reverse), and then
  everybody waits forever.
- `wait()` releases the lock while waiting and re-acquires it before returning,
  so the `while` re-check and the state change after it are atomic with respect
  to other `synchronized` methods on this object.
- `size()` is `synchronized` too: without it, a reader can see a stale `count`,
  or a torn read racing a concurrent `put` / `take`.
- The `items[head] = null` is not cosmetic: without it the array keeps
  references to items that were already taken, so they are never garbage
  collected.

## Three self-check questions (answer before you write)

1. **What state is shared between threads?** `items`, `head`, `tail`, `count`
   — every access to them must hold the monitor. If your answer leaves
   `size()` unsynchronized, stop: that is the race.
2. **Why `while`, not `if`, around `wait()`?** Two reasons: a spurious wakeup
   can return from `wait()` with the condition still false, and with several
   producers waiting, the first one woken can fill the freed slot before the
   second one re-acquires the lock. The second one must re-check and wait
   again instead of overfilling the buffer.
3. **Why `notifyAll()`, not `notify()`?** Producers and consumers wait on the
   same monitor. After a `take()` frees a slot, `notify()` may wake another
   consumer (which goes straight back to waiting — the buffer is empty) while
   every producer stays asleep, and nobody ever wakes the producers again.
   `notifyAll()` wakes both kinds; the ones whose condition is still false
   just wait again.

## Statement

Implement a fixed-capacity FIFO buffer. `put(item)` blocks when the buffer is
full until there is space; `take()` blocks when the buffer is empty until there
is an item. `size()` returns the current element count. Items come out in the
same order they went in.

## API

```java
public BoundedBuffer(int capacity)
public void put(T item) throws InterruptedException
public T take() throws InterruptedException
public int size()
```

## Examples

| capacity | operations | result |
|---|---|---|
| 2 | `put(1); put(2); take(); take()` | `1` then `2` (FIFO) |
| 1 | `put(1)` then a second `put(2)` | second `put` blocks until someone `take()`s |
| 2 | `take()` on an empty buffer | blocks until someone `put()`s |
| 3 | `put(1); put(2); put(3); take(); put(4); take(); take(); take()` | `1, 2, 3, 4` in order |

## Constraints

- `1 <= capacity <= 1000`
- Elements are non-null.
- `put` / `take` throw `InterruptedException` — don't swallow it: restore the
  interrupt flag if you catch it, or let it propagate.

## Common pitfalls

- `wait()` guarded by `if` instead of `while` — a spurious wakeup, or another
  thread taking the freed slot first, lets the thread proceed when the
  condition is false. The buffer overfills (or a `take()` reads an empty slot)
  and items are lost or duplicated.
- `notify()` instead of `notifyAll()` — with producers and consumers both
  waiting, it may wake only the same kind and the other side waits forever
  (missed notification → deadlock). The tests run several producers and
  consumers at once, so this fails.
- Reaching for `BlockingQueue` — it solves the problem for you and practices
  nothing. Hand-write the blocking logic.
- `size()` not `synchronized` — a reader racing a `put` / `take` sees a stale
  or torn count.
- Swallowing `InterruptedException` (catching it and carrying on as if nothing
  happened) — the waiting thread can then never be interrupted out of the
  buffer.
- Forgetting `items[head] = null` in `take()` — taken items stay referenced by
  the array and are never garbage collected.

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
