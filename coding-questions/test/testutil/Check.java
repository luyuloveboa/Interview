package testutil;

import java.util.Arrays;

/** Tiny assertion helper so the tests run with just a JDK (no JUnit / Maven needed). */
public final class Check {
    public static int passed = 0, failed = 0;

    private Check() {}

    public static void eq(String name, Object got, Object expected) {
        String g = show(got), e = show(expected);
        if (g.equals(e)) {
            passed++;
        } else {
            failed++;
            System.out.println("  FAIL " + name + ": got " + g + ", expected " + e);
        }
    }

    public static void section(String title) {
        System.out.println("== " + title);
    }

    private static String show(Object o) {
        if (o instanceof int[]) return Arrays.toString((int[]) o);
        if (o instanceof Object[]) return Arrays.deepToString((Object[]) o);
        return String.valueOf(o);
    }
}
