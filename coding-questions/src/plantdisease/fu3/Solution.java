package plantdisease.fu3;

import java.util.*;

public class Solution {
    static final int[] DR = {-1, -1, -1, 0, 0, 1, 1, 1};
    static final int[] DC = {-1, 0, 1, -1, 1, -1, 0, 1};
    int m, n, threshold;
    int[][] g, cnt;  // g: copy of grid; cnt[r][c]: number of infected neighbors

    // [FU3 CHANGED] + deathThreshold, returns {daysUntilEnd, deadCount}
    public int[] epidemicWithDeath(int[][] grid, int threshold, int recoveryDays, int deathThreshold) {
        m = grid.length; n = grid[0].length; this.threshold = threshold;
        g = Arrays.stream(grid).map(int[]::clone).toArray(int[][]::new);
        cnt = new int[m][n];
        List<int[]> seeds = new ArrayList<>();
        for (int r = 0; r < m; r++)
            for (int c = 0; c < n; c++)
                if (g[r][c] == 1) seeds.add(new int[]{r, c});
        List<List<int[]>> waves = new ArrayList<>(List.of(seeds));   // waves.get(d) = cells infected on day d
        int dead = 0;                                                  // [FU3 NEW]

        for (int day = 0; ; day++) {
            List<int[]> next = new ArrayList<>();       // cells infected on day+1
            for (int[] p : waves.get(day)) spread(p, +1, next);
            // [FU3 NEW] Fatal check: cnt here = infected neighbors in yesterday's state
            //           (today's recoveries not applied yet, same-day infections not counted).
            //           Dead and immune behave the same afterwards, so we only need to count.
            if (day == 0) for (int[] p : seeds) if (cnt[p[0]][p[1]] >= deathThreshold) dead++;
            for (int[] p : next) if (cnt[p[0]][p[1]] >= deathThreshold) dead++;
            // cells infected on day (day+1-R) stop being contagious on day+1
            int ended = day + 1 - recoveryDays;
            if (ended >= 0) for (int[] p : waves.get(ended)) spread(p, -1, next);
            if (next.isEmpty()) return new int[]{seeds.isEmpty() ? 0 : day + recoveryDays, dead};  // [FU3 CHANGED]
            for (int[] p : next) g[p[0]][p[1]] = 1;
            waves.add(next);
        }
    }

    // delta = +1 (p got infected) or -1 (p recovered); only +1 can push a cell to threshold
    void spread(int[] p, int delta, List<int[]> next) {
        for (int d = 0; d < 8; d++) {
            int x = p[0] + DR[d], y = p[1] + DC[d];
            if (x < 0 || y < 0 || x >= m || y >= n) continue;
            cnt[x][y] += delta;
            if (delta > 0 && cnt[x][y] == threshold && g[x][y] == 0) next.add(new int[]{x, y});
        }
    }
}
