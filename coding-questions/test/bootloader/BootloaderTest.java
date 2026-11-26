package bootloader;

import java.util.*;
import java.util.function.Supplier;
import testutil.Check;

public class BootloaderTest {
    public static void main(String[] args) { run(); }

    public static void run() {
        base();
        fu1();
        randomVsBruteForce();
    }

    static void base() {
        Check.section("Bootloader: base (first repeated instruction)");
        first("ex1", new String[]{"plus +1", "next +0", "plus +3", "jump -2", "plus +5"}, 1);
        first("ex2", new String[]{"plus +5", "jump +2", "plus +100", "next +0", "plus -3", "jump -3", "plus +10"}, 3);
        first("ex3", new String[]{"plus +2", "next +0", "jump +2", "plus +99", "plus +4"}, -1);
        first("jump +0 repeats itself", new String[]{"plus +1", "jump +0"}, 1);
        first("only instruction loops", new String[]{"jump +0"}, 0);
        first("single next ends", new String[]{"next +0"}, -1);
        first("single jump +1 ends", new String[]{"jump +1"}, -1);
        first("loop back to the start", new String[]{"plus +1", "jump -1"}, 0);
        first("next ignores its value", new String[]{"next +5"}, -1);
        first("loop entered in the middle", new String[]{"jump +2", "plus +1", "jump -1"}, 2);
        first("negative plus is parsed", new String[]{"plus -5", "jump +0"}, 1);
        String[] big = new String[100_000];
        Arrays.fill(big, "plus +1");
        big[big.length - 1] = "jump -" + (big.length - 1);
        first("1e5 instructions, loop back to 0", big, 0);
    }

    static void fu1() {
        Check.section("Bootloader: FU1 (repair one jump/next)");
        Check.eq("firstRepeatedInstruction still works: ex1",
                () -> new bootloader.fu1.Solution().firstRepeatedInstruction(new String[]{"plus +1", "next +0", "plus +3", "jump -2", "plus +5"}), 1);
        repair("ex1", new String[]{"plus +1", "next +0", "plus +3", "jump -2", "plus +5"}, 9);
        repair("ex2", new String[]{"plus +2", "next +0", "plus +4", "jump -1", "plus +10"}, 16);
        repair("ex3", new String[]{"plus +3", "next +0", "plus +4"}, 7);
        repair("already terminates: no flip", new String[]{"plus +2", "next +0", "jump +2", "plus +99", "plus +4"}, 6);
        repair("fix a jump +0", new String[]{"plus +1", "jump +0"}, 1);
        repair("fix by turning next into jump", new String[]{"next +3", "jump -1", "jump +0", "plus +1"}, 1);
        repair("fix by turning jump into next", new String[]{"next +2", "plus +100", "jump -2"}, 100);
        repair("negative plus values", new String[]{"plus -4", "plus -6", "jump -1"}, -10);
        repair("only instruction is the broken jump", new String[]{"jump +0"}, 0);
        repair("fix is deep in the path", new String[]{"plus +1", "plus +2", "plus +3", "jump +2", "jump -4", "plus +4"}, 10);
        String[] big = new String[100_000];
        Arrays.fill(big, "plus +1");
        big[big.length - 1] = "jump -" + (big.length - 1);
        repair("1e5 instructions (must be O(n))", big, big.length - 1);
    }

    static void first(String name, String[] ins, int expected) {
        Check.eq(name + ": firstRepeatedInstruction(" + show(ins) + ")",
                () -> new bootloader.base.Solution().firstRepeatedInstruction(ins), expected);
    }

    static void repair(String name, String[] ins, int expected) {
        Check.eq(name + ": repairedAccumulator(" + show(ins) + ")",
                () -> new bootloader.fu1.Solution().repairedAccumulator(ins), expected);
    }

    /** Random valid programs vs simulating every possible single flip. */
    static void randomVsBruteForce() {
        Check.section("Bootloader: random programs vs brute force");
        Random rnd = new Random(9);
        String[] ops = {"plus", "next", "jump"};
        int[] mismatches = new int[2], cases = new int[2];
        String[] first = new String[2];
        while (cases[1] < 3000) {
            int n = 1 + rnd.nextInt(12);
            String[] ins = new String[n];
            for (int i = 0; i < n; i++) ins[i] = ops[rnd.nextInt(3)] + " " + (rnd.nextInt(2 * n + 1) - n);
            if (!staysInRange(ins)) continue;                      // constraint: original run never leaves [0, n]

            cases[0]++;
            compare(0, mismatches, first, "firstRepeatedInstruction(" + show(ins) + ")",
                    () -> new bootloader.base.Solution().firstRepeatedInstruction(ins), bruteFirstRepeat(ins));

            Integer expected = simulate(ins, -1);                  // already terminates?
            if (expected == null) {                                // otherwise: exactly one flip must work
                int works = 0;
                for (int f = 0; f < n; f++) {
                    if (ins[f].startsWith("plus")) continue;
                    Integer acc = simulate(ins, f);
                    if (acc != null) { works++; expected = acc; }
                }
                if (works != 1) continue;
            }
            cases[1]++;
            compare(1, mismatches, first, "repairedAccumulator(" + show(ins) + ")",
                    () -> new bootloader.fu1.Solution().repairedAccumulator(ins), expected);
        }
        String[] names = {"base: random programs", "FU1: random programs"};
        for (int v = 0; v < 2; v++) {
            if (mismatches[v] == 0) Check.eq(names[v], 0, 0);
            else Check.fail(names[v], mismatches[v] + " of " + cases[v] + " random programs wrong. First one:\n" + first[v]);
        }
    }

    static void compare(int v, int[] mismatches, String[] first, String call, Supplier<?> got, Object expected) {
        Check.Outcome o = Check.attempt(got);
        if (o.matches(expected)) return;
        String detail = call + "\n  expected: " + Check.show(expected) + "\n  got:      " + o;
        if (o.timedOut) {
            Check.fail("random programs", detail);
            throw Check.abort();
        }
        if (mismatches[v]++ == 0) first[v] = detail;
    }

    /** Runs with instruction `flip` swapped (jump <-> next); null if it loops or leaves [0, n]. */
    static Integer simulate(String[] ins, int flip) {
        int n = ins.length, acc = 0, pc = 0;
        boolean[] seen = new boolean[n];
        while (pc != n) {
            if (pc < 0 || pc > n || seen[pc]) return null;
            seen[pc] = true;
            String[] p = ins[pc].split(" ");
            String op = p[0];
            int v = Integer.parseInt(p[1]);
            if (pc == flip) op = op.equals("jump") ? "next" : "jump";
            if (op.equals("plus")) acc += v;
            pc += op.equals("jump") ? v : 1;
        }
        return acc;
    }

    static int bruteFirstRepeat(String[] ins) {
        int n = ins.length, pc = 0;
        Set<Integer> seen = new HashSet<>();
        while (pc != n) {
            if (!seen.add(pc)) return pc;
            String[] p = ins[pc].split(" ");
            pc += p[0].equals("jump") ? Integer.parseInt(p[1]) : 1;
        }
        return -1;
    }

    static boolean staysInRange(String[] ins) {
        int n = ins.length, pc = 0;
        boolean[] seen = new boolean[n];
        while (pc != n) {
            if (pc < 0 || pc > n) return false;
            if (seen[pc]) return true;
            seen[pc] = true;
            String[] p = ins[pc].split(" ");
            pc += p[0].equals("jump") ? Integer.parseInt(p[1]) : 1;
        }
        return true;
    }

    static String show(String[] ins) {
        if (ins.length <= 8) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < ins.length; i++) sb.append(i > 0 ? ", " : "").append('"').append(ins[i]).append('"');
            return sb.append(']').toString();
        }
        return "[\"" + ins[0] + "\", ... " + ins.length + " instructions]";
    }
}
