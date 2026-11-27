package profilerevents.fu1;

import java.util.*;

public class Solution {
    // [FU1 NEW] The path of depth i in this sample is the SAME path as in the previous sample
    // iff i < common prefix. So per-path state can live in arrays indexed by depth.
    // A child is only present when its parent is, so streak never grows with depth and the
    // valid paths (streak >= n) are always a prefix of the stack: cur[0 .. valid).
    public List<String> generateEvents(List<String> samples, int n) {          // [FU1 CHANGED] + n
        List<String> out = new ArrayList<>();
        String[] prev = new String[0];
        int[] prevStreak = new int[0], prevStart = new int[0];                 // [FU1 NEW]
        int prevValid = 0;                                                     // [FU1 NEW]
        for (String sample : samples) {
            int colon = sample.indexOf(':');
            int time = Integer.parseInt(sample.substring(0, colon));
            String[] cur = parseStack(sample.substring(colon + 1));

            int cp = 0;                                            // common prefix, frame by frame
            while (cp < prev.length && cp < cur.length && prev[cp].equals(cur[cp])) cp++;

            // [FU1 NEW] extend the streaks of paths that are still there, restart the rest
            int[] streak = new int[cur.length], start = new int[cur.length];
            int valid = 0;
            for (int i = 0; i < cur.length; i++) {
                streak[i] = i < cp ? prevStreak[i] + 1 : 1;
                start[i] = i < cp ? prevStart[i] : time;
                if (streak[i] >= n) valid = i + 1;
            }

            // [FU1 CHANGED] diff the valid stacks instead of the raw ones.
            // Valid before and still valid now: same path (i < cp) and was valid (i < prevValid).
            int k = Math.min(cp, prevValid);
            for (int i = prevValid - 1; i >= k; i--) out.add("end:" + time + ":" + prev[i]);
            for (int i = k; i < valid; i++) out.add("start:" + start[i] + ":" + cur[i]);   // streak's first sample

            prev = cur;
            prevStreak = streak; prevStart = start; prevValid = valid;         // [FU1 NEW]
        }
        return out;
    }

    static String[] parseStack(String s) {
        return s.isEmpty() ? new String[0] : s.split("->");       // "".split gives [""], not []
    }
}
