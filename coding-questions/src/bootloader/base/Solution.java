package bootloader.base;

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
}
