# P05 — ThreadSafeCache with ReadWriteLock

Medium. Practices `ReadWriteLock` — read lock shared (many readers at once),
write lock exclusive (no reads or writes while writing).

## Pattern (memorize this shape)

One lock guards the whole map. Reads take the read lock, writes take the write
lock, and the lock is released in `finally`.

```java
private final Map<String, String> map = new HashMap<>();
private final ReadWriteLock lock = new ReentrantReadWriteLock();

public String get(String key) {
    lock.readLock().lock();                       // shared: many readers at once
    try {
        return map.get(key);
    } finally {
        lock.readLock().unlock();
    }
}

public void put(String key, String value) {
    lock.writeLock().lock();                      // exclusive: no reads or writes
    try {
        map.put(key, value);
    } finally {
        lock.writeLock().unlock();
    }
}

public int size() {
    lock.readLock().lock();                       // size() is a read, not a write
    try {
        return map.size();
    } finally {
        lock.readLock().unlock();
    }
}
```

- The read lock is shared: any number of threads can hold it at the same time,
  so concurrent `get()` calls run in parallel.
- The write lock is exclusive: while a thread holds it, no other thread can hold
  the read lock or the write lock, so a `put()` never runs while a `get()` is
  reading the map.
- `unlock()` goes in `finally`: if the map access throws, the lock is still
  released. A lock that is never released blocks every later call forever.
- There is exactly one lock, shared by all three methods. A lock created inside
  a method is a fresh lock on every call and protects nothing.
- `size()` takes the read lock, not the write lock — it only reads. Taking the
  write lock for a read is correct but serializes reads, which defeats the
  point of the exercise.

## Three self-check questions (answer before you write)

1. **What state is shared between threads?** Here: the `HashMap` — every access
   to it must hold the lock. If your answer says `get()` can read the map
   without a lock because "reads don't change anything", stop: a `get()` that
   runs while a `put()` is resizing the map can see a half-moved table and
   miss a key that is already there.
2. **Which lock does each method take?** Read lock for `get()` and `size()`,
   write lock for `put()`. What breaks if `put()` takes the read lock? Two
   writers resize the map at the same time and entries get lost. What breaks
   if `get()` takes the write lock? Nothing breaks — but reads serialize, and
   you have rebuilt `synchronized` with extra steps.
3. **What happens if you forget `unlock()`, or unlock outside `finally`?** A
   `put()` that throws between `lock()` and `unlock()` leaves the write lock
   held, and every later call blocks forever. And why is `synchronized` on
   every method not the answer here? It is correct — but it serializes reads,
   losing the read concurrency this problem wants.

## Statement

Implement a thread-safe key-value cache backed by a `HashMap` and guarded by a
`ReadWriteLock`. `get(key)` returns the value for `key`, or `null` if the key
is absent. `put(key, value)` writes the value, overwriting any previous value
for the same key. `size()` returns the number of distinct keys. Reads
(`get`, `size`) take the read lock; writes (`put`) take the write lock.

## API

```java
public String get(String key);
public void put(String key, String value);
public int size()
```

## Examples

| operations | result |
|---|---|
| `put("a", "1"); get("a")` | `"1"` |
| `get("missing")` | `null` |
| `put("a", "1"); put("a", "2"); get("a")` | `"2"` |
| `put("a", "1"); put("a", "2"); size()` | `1` |

## Constraints

- Key and value are non-null strings.
- `get()` returns `null` for an absent key; `put()` overwrites an existing key;
  `size()` counts distinct keys.

## Common pitfalls

- A plain `HashMap` with no lock — concurrent writes lose data or corrupt the
  map. The stress test puts distinct keys from many threads at once; an
  unlocked map comes back short.
- Using `synchronized` on every method — correct, but it serializes reads,
  losing the read concurrency this problem wants. The review checks that
  `ReadWriteLock` is used.
- `put()` under the read lock — two writers can then resize the map at the
  same time, and entries get lost. Writes need the write lock.
- `get()` or `size()` under the write lock — correct, but it serializes reads,
  same as the `synchronized` pitfall above.
- `unlock()` outside `finally`, or forgetting `unlock()` — a throw between
  `lock()` and `unlock()` leaves the lock held and every later call blocks
  forever.
- Creating a new lock inside each method — every call gets its own lock, so
  no two calls ever exclude each other. One lock, shared by all methods.

## How to run the tests

From `coding-questions/multithread/`:

```bash
./run-tests.sh          # macOS / Linux
```

```powershell
.\run-tests.ps1         # Windows PowerShell
```

Or open `coding-questions/multithread/` in VS Code and run the "All tests" launch
configuration. All tests pass when the last line is `ALL PASSED (... checks)`
(12 for P05).
