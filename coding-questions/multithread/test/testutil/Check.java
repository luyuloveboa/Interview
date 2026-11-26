package testutil;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Tiny assertion helper so the tests run with just a JDK (no JUnit / Maven needed).
 *
 * Failures print the section, the check name, expected vs. got, and (for arrays / lists) the
 * first index that differs. Checks given as a lambda also catch exceptions and time out
 * infinite loops, so one broken method does not stop the rest of the run.
 */
public final class Check {
    public static int passed = 0, failed = 0;
    public static long timeoutMs = 2000;

    private static String section = "";
    private static final List<String> failures = new ArrayList<>();
    private static final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "check");
        t.setDaemon(true);                                         // a timed-out call must not keep the JVM alive
        return t;
    });

    private Check() {}

    public static void section(String title) {
        section = title;
        System.out.println("== " + title);
    }

    /** Compares a value that was already computed. */
    public static void eq(String name, Object got, Object expected) {
        eq(name, Outcome.of(got), expected);
    }

    /** Runs {@code got} (catching exceptions and timeouts), then compares its result. */
    public static void eq(String name, Supplier<?> got, Object expected) {
        eq(name, attempt(got), expected);
    }

    private static void eq(String name, Outcome got, Object expected) {
        if (got.matches(expected)) {
            passed++;
            return;
        }
        StringBuilder msg = new StringBuilder()
                .append("expected: ").append(show(expected)).append('\n')
                .append("got:      ").append(got);
        if (got.error == null) {
            String diff = diff(got.value, expected);
            if (diff != null) msg.append('\n').append(diff);
        }
        fail(name, msg.toString());
        if (got.timedOut) throw abort();
    }

    /**
     * Thrown after a timeout to end the current suite. Java cannot stop the stuck call, and it
     * would keep burning CPU / memory and break every later check, so the suite (which runs in
     * its own JVM, see AllTests) stops here and the other suites still run.
     */
    public static final class Aborted extends RuntimeException {
        Aborted() { super(null, null, false, false); }
    }

    public static Aborted abort() {
        System.out.println("  STOPPING this suite: the timed-out call can't be stopped and would break later checks.");
        System.out.println("  Fix it and re-run to see the remaining results.");
        return new Aborted();
    }

    /** Records a failure with a free-form, possibly multi-line explanation. */
    public static void fail(String name, String details) {
        failed++;
        String label = section.isEmpty() ? name : "[" + section + "] " + name;
        failures.add(label);
        System.out.println("  FAIL " + label);
        for (String line : details.split("\n")) System.out.println("       " + line);
    }

    /** Names of all failed checks, for the end-of-run summary. */
    public static List<String> failures() { return Collections.unmodifiableList(failures); }

    /** Runs {@code f} with a timeout; never throws. */
    public static Outcome attempt(Supplier<?> f) {
        Future<?> fut = pool.submit(f::get);
        try {
            return Outcome.of(fut.get(timeoutMs, TimeUnit.MILLISECONDS));
        } catch (TimeoutException e) {
            fut.cancel(true);                                      // Java cannot kill the thread; it keeps running until it ends or runs out of memory
            return Outcome.timeout("timed out after " + timeoutMs + " ms (infinite loop?)");
        } catch (ExecutionException e) {
            return Outcome.error(describe(e.getCause()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Outcome.error("interrupted");
        }
    }

    /** Either a returned value or a description of what went wrong. */
    public static final class Outcome {
        public final Object value;
        public final String error;
        public final boolean timedOut;

        private Outcome(Object value, String error, boolean timedOut) {
            this.value = value; this.error = error; this.timedOut = timedOut;
        }

        static Outcome of(Object value) { return new Outcome(value, null, false); }
        static Outcome error(String error) { return new Outcome(null, error, false); }
        static Outcome timeout(String error) { return new Outcome(null, error, true); }

        public boolean matches(Object expected) { return error == null && show(value).equals(show(expected)); }

        @Override public String toString() { return error != null ? error : show(value); }
    }

    public static String show(Object o) {
        if (o instanceof int[]) return Arrays.toString((int[]) o);
        if (o instanceof long[]) return Arrays.toString((long[]) o);
        if (o instanceof boolean[]) return Arrays.toString((boolean[]) o);
        if (o instanceof int[][]) return Arrays.deepToString((int[][]) o);
        if (o instanceof Object[]) return Arrays.deepToString((Object[]) o);
        return String.valueOf(o);
    }

    /** For two sequences, says where they first differ; null otherwise. */
    private static String diff(Object got, Object expected) {
        List<?> g = asList(got), e = asList(expected);
        if (g == null || e == null) return null;
        for (int i = 0; i < Math.min(g.size(), e.size()); i++) {
            if (!show(g.get(i)).equals(show(e.get(i))))
                return "first difference at index " + i + ": got " + show(g.get(i)) + ", expected " + show(e.get(i));
        }
        if (g.size() != e.size()) return "length differs: got " + g.size() + ", expected " + e.size();
        return null;
    }

    private static List<?> asList(Object o) {
        if (o instanceof List) return (List<?>) o;
        if (o instanceof Object[]) return Arrays.asList((Object[]) o);
        if (o instanceof int[]) return Arrays.stream((int[]) o).boxed().toList();
        if (o instanceof long[]) return Arrays.stream((long[]) o).boxed().toList();
        return null;
    }

    /** Runs a whole test suite; if it crashes, records that and lets the other suites run. */
    public static void suite(String name, Runnable body) {
        try {
            body.run();
        } catch (Aborted a) {
            // already reported
        } catch (Throwable t) {
            fail(name + " suite crashed", describe(t) + "\n(checks after this point in the suite did not run)");
        }
    }

    public static String describe(Throwable t) {
        StringBuilder sb = new StringBuilder("threw ").append(t);
        StackTraceElement[] st = t.getStackTrace();
        for (int i = 0; i < Math.min(5, st.length); i++) {
            String cls = st[i].getClassName();
            if (cls.startsWith("testutil.") || cls.startsWith("java.util.concurrent.")) break;
            sb.append("\n    at ").append(st[i]);
        }
        return sb.toString();
    }
}
