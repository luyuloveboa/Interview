package bootloader.fu1;

import java.util.*;

public class Solution {
    int n;
    String[] op;
    int[] val;

    void parse(String[] instructions) {
        n = instructions.length;
        op = new String[n];
        val = new int[n];
        for (int i = 0; i < n; i++) {
            String[] p = instructions[i].split(" ");
            op[i] = p[0];
            val[i] = Integer.parseInt(p[1]);             // parseInt accepts "+3" and "-2"
        }
    }

    /** Where control goes after instruction i. */
    int next(int i) { return op[i].equals("jump") ? i + val[i] : i + 1; }

    // ---------------- base ----------------
    public int firstRepeatedInstruction(String[] instructions) {
        parse(instructions);
        boolean[] seen = new boolean[n];
        for (int pc = 0; pc != n; pc = next(pc)) {
            if (seen[pc]) return pc;
            seen[pc] = true;
        }
        return -1;
    }

    // ---------------- [FU1 NEW] repair one jump/next ----------------
    // 1. canEnd[i] = starting at i (with no flip) reaches pc == n. Computed backwards from n
    //    over the reversed edges i -> next(i), so O(n) total.
    // 2. Walk the original path. At the first jump/next whose FLIPPED target can reach the end,
    //    flip it and run to the end. Everything before that point is the same as the original run.
    public int repairedAccumulator(String[] instructions) {
        if (firstRepeatedInstruction(instructions) == -1) return run(-1);   // already terminates

        List<List<Integer>> from = new ArrayList<>();    // from.get(t) = instructions that go to t
        for (int t = 0; t <= n; t++) from.add(new ArrayList<>());
        for (int i = 0; i < n; i++) {
            int t = next(i);
            if (t >= 0 && t <= n) from.get(t).add(i);
        }
        boolean[] canEnd = new boolean[n + 1];
        Deque<Integer> queue = new ArrayDeque<>(List.of(n));
        canEnd[n] = true;
        while (!queue.isEmpty())
            for (int i : from.get(queue.poll()))
                if (!canEnd[i]) { canEnd[i] = true; queue.add(i); }

        boolean[] seen = new boolean[n];
        for (int pc = 0; !seen[pc]; pc = next(pc)) {
            seen[pc] = true;
            if (op[pc].equals("plus")) continue;
            int flipped = op[pc].equals("jump") ? pc + 1 : pc + val[pc];
            if (flipped >= 0 && flipped <= n && canEnd[flipped]) return run(pc);
        }
        throw new IllegalStateException("no single flip fixes the program");
    }

    /** Runs the program with instruction `flip` swapped (jump <-> next); flip = -1 means no swap. */
    int run(int flip) {
        int acc = 0;
        for (int pc = 0; pc != n; ) {
            String o = op[pc];
            if (pc == flip) o = o.equals("jump") ? "next" : "jump";
            if (o.equals("plus")) acc += val[pc];
            pc = o.equals("jump") ? pc + val[pc] : pc + 1;
        }
        return acc;
    }
}
