package profilerevents.fu2;

import java.util.*;

public class Solution {
    // [FU2 CHANGED] Mirror image of FU1: paths are suffixes (counted from the leaf), so compare
    // stacks from the end. Arrays are indexed by j = suffix length - 1; suffix j's outermost frame
    // is stack[len - 1 - j]. The valid suffixes are always lengths 1 .. valid.
    public List<String> generateEvents(List<String> samples, int n) {
        List<String> out = new ArrayList<>();
        String[] prev = new String[0];
        int[] prevStreak = new int[0], prevStart = new int[0];
        int prevValid = 0;
        for (String sample : samples) {
            int colon = sample.indexOf(':');
            int time = Integer.parseInt(sample.substring(0, colon));
            String[] cur = parseStack(sample.substring(colon + 1));
            int pl = prev.length, cl = cur.length;

            int cs = 0;                                            // [FU2 CHANGED] common suffix
            while (cs < pl && cs < cl && prev[pl - 1 - cs].equals(cur[cl - 1 - cs])) cs++;

            int[] streak = new int[cl], start = new int[cl];
            int valid = 0;
            for (int j = 0; j < cl; j++) {
                streak[j] = j < cs ? prevStreak[j] + 1 : 1;
                start[j] = j < cs ? prevStart[j] : time;
                if (streak[j] >= n) valid = j + 1;
            }

            int k = Math.min(cs, prevValid);
            // [FU2 CHANGED] ends: shortest suffix first (innermost frame); starts: longest first (outermost)
            for (int j = k; j < prevValid; j++) out.add("end:" + time + ":" + prev[pl - 1 - j]);
            for (int j = valid - 1; j >= k; j--) out.add("start:" + start[j] + ":" + cur[cl - 1 - j]);

            prev = cur;
            prevStreak = streak; prevStart = start; prevValid = valid;
        }
        return out;
    }

    static String[] parseStack(String s) {
        return s.isEmpty() ? new String[0] : s.split("->");       // "".split gives [""], not []
    }
}
