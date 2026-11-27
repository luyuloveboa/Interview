# Multithreading practice

The P01–P09 multithreading exercises — extra practice, not bank questions. Each exercise
has its own starter (only the method signature — you write the implementation) and tests.
Work through them in order, P01 to P09, easiest first. Implement one exercise, run the
tests, and move on only when they are all green.

| Exercise | Code | Statement |
|---|---|---|
| P01 — Parallel Array Sum (Easy) | `src/parallelsum/base/ParallelSum.java` | [problems/p01-parallel-sum.md](problems/p01-parallel-sum.md) |
| P02 — Parallel Squares with CountDownLatch (Easy) | `src/parallelsquares/base/ParallelSquares.java` | [problems/p02-parallel-squares.md](problems/p02-parallel-squares.md) |
| P03 — Parallel Unique URL Processor (Medium) | `src/uniqueurl/base/UniqueUrlProcessor.java` | [problems/p03-unique-url-processor.md](problems/p03-unique-url-processor.md) |
| P04 — BlockingQueue Task Pipeline (Medium) | `src/taskpipeline/base/TaskPipeline.java` | [problems/p04-task-pipeline.md](problems/p04-task-pipeline.md) |
| P05 — ThreadSafeCache with ReadWriteLock (Medium) | `src/cache/base/ThreadSafeCache.java` | [problems/p05-thread-safe-cache.md](problems/p05-thread-safe-cache.md) |
| P06 — BoundedBuffer with wait/notify (Hard) | `src/boundedbuffer/base/BoundedBuffer.java` | [problems/p06-bounded-buffer.md](problems/p06-bounded-buffer.md) |
| P07 — ConnectionPool with Semaphore (Medium) | `src/connectionpool/base/ConnectionPool.java` | [problems/p07-connection-pool.md](problems/p07-connection-pool.md) |
| P08 — Parallel Range Counter with ForkJoinPool (Medium) | `src/rangecounter/base/RangeCounter.java` | [problems/p08-range-counter.md](problems/p08-range-counter.md) |
| P09 — Dynamic Task Tree with Phaser (Medium) | `src/tasktree/base/TaskTree.java` | [problems/p09-dynamic-task-tree.md](problems/p09-dynamic-task-tree.md) |

## Layout

```
multithread/
├── src/                     # starters, one package per exercise
│   ├── parallelsum/base/
│   ├── parallelsquares/base/
│   ├── uniqueurl/base/
│   ├── taskpipeline/base/
│   ├── cache/base/
│   ├── boundedbuffer/base/
│   ├── connectionpool/base/
│   ├── rangecounter/base/
│   └── tasktree/base/
├── test/                    # tests (only JDK needed, no JUnit / Maven)
│   ├── AllTests.java        # entry point: runs every suite
│   ├── parallelsum/ParallelSumTest.java
│   ├── parallelsquares/ParallelSquaresTest.java
│   ├── uniqueurl/UniqueUrlProcessorTest.java
│   ├── taskpipeline/TaskPipelineTest.java
│   ├── cache/ThreadSafeCacheTest.java
│   ├── boundedbuffer/BoundedBufferTest.java
│   ├── connectionpool/ConnectionPoolTest.java
│   ├── rangecounter/RangeCounterTest.java
│   ├── tasktree/TaskTreeTest.java
│   └── testutil/Check.java  # assertion helper (copy of coding-questions/test/testutil/Check.java)
├── problems/                # statements, in English
├── run-tests.sh             # macOS / Linux
├── run-tests.ps1            # Windows PowerShell
└── .vscode/                 # VS Code config (source paths + launch configs)
```

The tests include, for each exercise:
- every example from the statement;
- cases aimed at the easy-to-make bugs (for P01: a shared `long` updated without
  synchronization, returning before all futures complete, a missing `shutdown()`,
  uneven splits dropping elements; for P02: returning before `latch.await()`,
  `countDown()` outside `finally`, a missing `shutdown()`, uneven splits
  dropping elements; for P03: two-step contains-then-add instead of an atomic
  claim, a missing `shutdown()`; for P04: a missing poison pill, a queue
  capacity that deadlocks the producer; for P05: an unsynchronized map, a write
  lock held for reads; for P06: `if` instead of `while` around `wait()`,
  `notify()` instead of `notifyAll()`; for P07: a bare `Semaphore` that lets a
  double release create an extra permit, releasing an unknown id; for P08:
  `fork()` without `join()`, counting without splitting; for P09: registering
  with the `Phaser` after submitting the task, a non-atomic claim);
- random datasets compared against a direct sequential computation.

## Running in VS Code

1. **Install JDK 17 or later** (Temurin JDK 21 recommended: https://adoptium.net/).
   Run `java -version` and `javac -version` in a terminal; both must print something.
2. **Install the VS Code extension** "Extension Pack for Java" (by Microsoft).
3. **Open `coding-questions/multithread/` as the folder**: File → Open Folder, pick
   `multithread` — not the whole `Interview` repo, otherwise the source paths in
   `.vscode/settings.json` do not apply. Wait for the Java extension to finish loading
   ("Java: Ready" in the status bar).
4. **Run the tests**, any of these:
   - **Run and Debug panel** (▷ icon in the left bar, or `Ctrl+Shift+D` / `Cmd+Shift+D`):
     pick "All tests" at the top and press F5. Or pick one exercise's tests.
   - **Click Run** directly: open `test/AllTests.java` and click "Run" above `main`.
   - **Terminal** (`` Ctrl+` ``):
     ```bash
     ./run-tests.sh          # macOS / Linux
     ```
     ```powershell
     .\run-tests.ps1         # Windows PowerShell
     ```
     If PowerShell refuses to run the script, run once:
     `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`.

All tests pass when the last line is:

```
ALL PASSED (102 checks)
```

A failure prints `FAIL <name>: got ..., expected ...` and the exit code is 1.

## Running without the scripts

```bash
javac -d out $(find src test -name '*.java')
java -cp out AllTests
```

## How to practice

Open the exercise's starter in `src/`, fill in the `TODO` method, and run the tests.
Do not look at the tests first for the answer — but do read them after a failure:
each failing check names the case that broke, and the random datasets catch bugs the
examples do not cover.
