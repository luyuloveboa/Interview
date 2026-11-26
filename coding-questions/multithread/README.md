# Multithreading practice

The P01–P09 multithreading exercises — extra practice, not bank questions. Each exercise
has its own starter (only the method signature — you write the implementation) and tests.
Work through them in order, P01 to P09, easiest first. Implement one exercise, run the
tests, and move on only when they are all green.

| Exercise | Code | Statement |
|---|---|---|
| P01 — Parallel Array Sum (Easy) | `src/parallelsum/base/ParallelSum.java` | [problems/p01-parallel-sum.md](problems/p01-parallel-sum.md) |

More exercises (P02–P09) are added one at a time, in the same shape.

## Layout

```
multithread/
├── src/                     # starters, one package per exercise
│   └── parallelsum/base/
├── test/                    # tests (only JDK needed, no JUnit / Maven)
│   ├── AllTests.java        # entry point: runs every suite
│   ├── parallelsum/ParallelSumTest.java
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
  uneven splits dropping elements);
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
ALL PASSED (10 checks)
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
