package profilerevents.base;

import java.util.*;

public class Solution {
    /** Diff each stack against the previous one: frames past the common prefix ended / started. */
    public List<String> generateEvents(List<String> samples) {
        List<String> out = new ArrayList<>();
        String[] prev = new String[0];
        for (String sample : samples) {
            int colon = sample.indexOf(':');
            int time = Integer.parseInt(sample.substring(0, colon));
            String[] cur = parseStack(sample.substring(colon + 1));

            int k = 0;                                             // common prefix, frame by frame
            while (k < prev.length && k < cur.length && prev[k].equals(cur[k])) k++;

            for (int i = prev.length - 1; i >= k; i--) out.add("end:" + time + ":" + prev[i]);   // inner -> outer
            for (int i = k; i < cur.length; i++) out.add("start:" + time + ":" + cur[i]);       // outer -> inner
            prev = cur;
        }
        return out;
    }

    static String[] parseStack(String s) {
        return s.isEmpty() ? new String[0] : s.split("->");       // "".split gives [""], not []
    }
}
