package plantdisease.fu1;

import java.util.*;

// [FU1] No code change needed:
//   - immune (2) is never a seed (only 1s are), so it never adds to anyone's count
//   - immune is never infected, because spread() only infects cells with g == 0
public class Solution {
    static final int[] DR = {-1, -1, -1, 0, 0, 1, 1, 1};
    static final int[] DC = {-1, 0, 1, -1, 1, -1, 0, 1};
    int m, n, threshold;
    int[][] g, cnt;  // g: copy of grid; cnt[r][c]: number of infected neighbors

    public int daysToStabilize(int[][] grid, int threshold) {
        m = grid.length; n = grid[0].length; this.threshold = threshold;
        g = Arrays.stream(grid).map(int[]::clone).toArray(int[][]::new);
        cnt = new int[m][n];
        List<int[]> today = new ArrayList<>();
        for (int r = 0; r < m; r++)
            for (int c = 0; c < n; c++)
                if (g[r][c] == 1) today.add(new int[]{r, c});

        for (int day = 0; ; day++) {
            List<int[]> next = new ArrayList<>();       // cells infected on day+1
            for (int[] p : today) spread(p, next);
            if (next.isEmpty()) return day;
            for (int[] p : next) g[p[0]][p[1]] = 1;
            today = next;
        }
    }

    // p just got infected: bump its neighbors' counts, collect healthy ones that just hit threshold
    void spread(int[] p, List<int[]> next) {
        for (int d = 0; d < 8; d++) {
            int x = p[0] + DR[d], y = p[1] + DC[d];
            if (x < 0 || y < 0 || x >= m || y >= n) continue;
            if (++cnt[x][y] == threshold && g[x][y] == 0) next.add(new int[]{x, y});   // g == 0 skips immune
        }
    }
}
