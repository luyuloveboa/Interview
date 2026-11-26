package plantdisease.fu2;

import java.util.*;

public class Solution {
    static final int[] DR = {-1, -1, -1, 0, 0, 1, 1, 1};
    static final int[] DC = {-1, 0, 1, -1, 1, -1, 0, 1};
    int m, n, threshold;
    int[][] g, cnt;  // g: copy of grid; cnt[r][c]: number of infected neighbors

    public int daysToStabilize(int[][] grid, int threshold, int recoveryDays) {   // [FU2 CHANGED] + recoveryDays
        m = grid.length; n = grid[0].length; this.threshold = threshold;
        g = Arrays.stream(grid).map(int[]::clone).toArray(int[][]::new);
        cnt = new int[m][n];
        List<int[]> seeds = new ArrayList<>();                                     // [FU2 CHANGED] today -> seeds
        for (int r = 0; r < m; r++)
            for (int c = 0; c < n; c++)
                if (g[r][c] == 1) seeds.add(new int[]{r, c});
        List<List<int[]>> waves = new ArrayList<>(List.of(seeds));               // [FU2 NEW] waves.get(d) = cells infected on day d

        for (int day = 0; ; day++) {
            List<int[]> next = new ArrayList<>();       // cells infected on day+1
            for (int[] p : waves.get(day)) spread(p, +1, next);                    // [FU2 CHANGED]
            // [FU2 NEW] cells infected on day (day+1-R) stop being contagious on day+1.
            //           g stays 1 for them: they just stop counting, and g != 0 means never reinfected.
            int ended = day + 1 - recoveryDays;
            if (ended >= 0) for (int[] p : waves.get(ended)) spread(p, -1, next);
            if (next.isEmpty()) return seeds.isEmpty() ? 0 : day + recoveryDays;  // [FU2 CHANGED] stable once the last wave recovers
            for (int[] p : next) g[p[0]][p[1]] = 1;
            waves.add(next);                                                       // [FU2 CHANGED]
        }
    }

    // [FU2 CHANGED] delta = +1 (p got infected) or -1 (p recovered); only +1 can push a cell to threshold
    void spread(int[] p, int delta, List<int[]> next) {
        for (int d = 0; d < 8; d++) {
            int x = p[0] + DR[d], y = p[1] + DC[d];
            if (x < 0 || y < 0 || x >= m || y >= n) continue;
            cnt[x][y] += delta;                                                    // [FU2 CHANGED]
            if (delta > 0 && cnt[x][y] == threshold && g[x][y] == 0) next.add(new int[]{x, y});
        }
    }
}
