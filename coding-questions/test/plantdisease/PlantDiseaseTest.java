package plantdisease;

import java.util.*;
import testutil.Check;

public class PlantDiseaseTest {
    public static void main(String[] args) { run(); }

    public static void run() {
        Check.section("Plant disease: base");
        Check.eq("ex1", new plantdisease.base.Solution().daysToStabilize(g("1,0,0|0,0,0|0,0,0"), 1), 2);
        Check.eq("ex2", new plantdisease.base.Solution().daysToStabilize(g("1,0|0,0"), 2), 0);
        Check.eq("ex3", new plantdisease.base.Solution().daysToStabilize(g("1,1,0|0,0,0|0,0,0"), 2), 3);
        Check.eq("no infected at all", new plantdisease.base.Solution().daysToStabilize(g("0,0|0,0"), 1), 0);

        Check.section("Plant disease: FU1 (immune)");
        Check.eq("ex1", new plantdisease.fu1.Solution().daysToStabilize(g("1,0,0|0,2,0|0,0,0"), 1), 3);
        Check.eq("ex2", new plantdisease.fu1.Solution().daysToStabilize(g("1,1|2,0"), 2), 1);
        Check.eq("ex3", new plantdisease.fu1.Solution().daysToStabilize(g("1,2,0|2,2,0|0,0,0"), 2), 0);

        Check.section("Plant disease: FU2 (recovery)");
        Check.eq("ex1", new plantdisease.fu2.Solution().daysToStabilize(g("1,0,0|0,0,0|0,0,0"), 1, 2), 4);
        Check.eq("ex2", new plantdisease.fu2.Solution().daysToStabilize(g("1,0"), 1, 1), 2);
        Check.eq("ex3", new plantdisease.fu2.Solution().daysToStabilize(g("1,2|2,0"), 2, 3), 3);
        // recovered cells must stop counting (needs the -1 delta)
        Check.eq("recovered stop counting", new plantdisease.fu2.Solution().daysToStabilize(g("0,0,0|0,1,1"), 2, 1), 2);
        // recovered cells must never be reinfected (checking the original grid instead of g loops forever)
        Check.eq("no reinfection", new plantdisease.fu2.Solution().daysToStabilize(g("0,0,1"), 1, 1), 3);

        Check.section("Plant disease: FU3 (death)");
        Check.eq("ex1", new plantdisease.fu3.Solution().epidemicWithDeath(g("1,0,0|0,0,0|0,0,0"), 1, 1, 1), new int[]{3, 8});
        Check.eq("ex2", new plantdisease.fu3.Solution().epidemicWithDeath(g("1,0,0|0,2,0|0,0,0"), 1, 2, 8), new int[]{5, 0});
        Check.eq("ex3", new plantdisease.fu3.Solution().epidemicWithDeath(g("1,0,0,0|0,0,0,0|0,0,0,0|0,0,0,0"), 1, 3, 3), new int[]{6, 2});

        Check.section("Plant disease: FU4 (firebreak)");
        Check.eq("ex1", new plantdisease.fu4.Solution().minDeathsWithFirebreak(g("1,0,0|0,0,0|0,0,1"), 1, 1, 1), 4);
        Check.eq("ex2", new plantdisease.fu4.Solution().minDeathsWithFirebreak(g("1,0,0|0,0,0|0,0,0"), 1, 1, 1), 0);
        Check.eq("ex3", new plantdisease.fu4.Solution().minDeathsWithFirebreak(g("1,1,0,0|1,0,2,0|0,2,0,1|0,0,1,1"), 2, 2, 2), 5);

        randomVsBruteForce();
    }

    /** Compares every version against a slow, obviously-correct day-by-day simulation. */
    static void randomVsBruteForce() {
        Check.section("Plant disease: random grids vs brute force");
        Random rnd = new Random(42);
        int mismatches = 0;
        for (int it = 0; it < 3000; it++) {
            int[][] grid = randomGrid(rnd);
            int t = 1 + rnd.nextInt(4), rd = 1 + rnd.nextInt(4), dt = 1 + rnd.nextInt(8);
            if (new plantdisease.base.Solution().daysToStabilize(noImmune(grid), t) != brute(noImmune(grid), t, 0, 99)[0]) mismatches++;
            if (new plantdisease.fu1.Solution().daysToStabilize(grid, t) != brute(grid, t, 0, 99)[0]) mismatches++;
            if (new plantdisease.fu2.Solution().daysToStabilize(grid, t, rd) != brute(grid, t, rd, 99)[0]) mismatches++;
            if (!Arrays.equals(new plantdisease.fu3.Solution().epidemicWithDeath(grid, t, rd, dt), brute(grid, t, rd, dt))) mismatches++;
            if (it < 800 && new plantdisease.fu4.Solution().minDeathsWithFirebreak(grid, t, rd, dt) != bruteFirebreak(grid, t, rd, dt)) mismatches++;
        }
        Check.eq("mismatches", mismatches, 0);
    }

    /**
     * Naive simulation. recoveryDays = 0 means plants never recover.
     * Returns {last day anything changed, number of deaths}.
     */
    static int[] brute(int[][] grid, int threshold, int recoveryDays, int deathThreshold) {
        int m = grid.length, n = grid[0].length;
        int[][] s = copy(grid), infectedOn = new int[m][n];
        boolean[][] fatal = new boolean[m][n];
        for (int i = 0; i < m; i++)
            for (int j = 0; j < n; j++)
                if (s[i][j] == 1) fatal[i][j] = infectedNeighbors(s, i, j) >= deathThreshold;
        int last = 0, dead = 0;
        for (int day = 1; ; day++) {
            List<int[]> infections = new ArrayList<>(), endings = new ArrayList<>();
            for (int i = 0; i < m; i++)
                for (int j = 0; j < n; j++) {
                    if (s[i][j] == 0) {
                        int k = infectedNeighbors(s, i, j);
                        if (k >= threshold) infections.add(new int[]{i, j, k});
                    } else if (s[i][j] == 1 && recoveryDays > 0 && infectedOn[i][j] + recoveryDays == day) {
                        endings.add(new int[]{i, j});
                    }
                }
            for (int[] p : endings) {
                if (fatal[p[0]][p[1]]) { s[p[0]][p[1]] = 3; dead++; } else s[p[0]][p[1]] = 2;
            }
            for (int[] p : infections) {
                s[p[0]][p[1]] = 1; infectedOn[p[0]][p[1]] = day; fatal[p[0]][p[1]] = p[2] >= deathThreshold;
            }
            if (!infections.isEmpty() || !endings.isEmpty()) last = day;
            boolean anyInfected = false;
            for (int[] row : s) for (int v : row) if (v == 1) anyInfected = true;
            if (infections.isEmpty() && (recoveryDays == 0 || !anyInfected)) return new int[]{last, dead};
        }
    }

    static int bruteFirebreak(int[][] grid, int t, int rd, int dt) {
        int m = grid.length, n = grid[0].length, best = brute(grid, t, rd, dt)[1];
        for (int k = 0; k < m + n; k++) {
            int[][] b = copy(grid);
            for (int r = 0; r < m; r++) for (int c = 0; c < n; c++) if (k == r || k == m + c) b[r][c] = 4;
            best = Math.min(best, brute(b, t, rd, dt)[1]);
        }
        return best;
    }

    static int infectedNeighbors(int[][] s, int i, int j) {
        int k = 0;
        for (int x = i - 1; x <= i + 1; x++)
            for (int y = j - 1; y <= j + 1; y++)
                if (x >= 0 && x < s.length && y >= 0 && y < s[0].length && !(x == i && y == j) && s[x][y] == 1) k++;
        return k;
    }

    static int[][] randomGrid(Random rnd) {
        int m = 1 + rnd.nextInt(7), n = 1 + rnd.nextInt(7);
        int[][] g = new int[m][n];
        for (int[] row : g)
            for (int j = 0; j < n; j++) {
                double x = rnd.nextDouble();
                row[j] = x < 0.15 ? 1 : x < 0.25 ? 2 : 0;
            }
        return g;
    }

    static int[][] noImmune(int[][] g) {
        int[][] c = copy(g);
        for (int[] row : c) for (int j = 0; j < row.length; j++) if (row[j] == 2) row[j] = 0;
        return c;
    }

    static int[][] copy(int[][] g) { return Arrays.stream(g).map(int[]::clone).toArray(int[][]::new); }

    /** "1,0|0,0" -> {{1,0},{0,0}} */
    static int[][] g(String s) {
        return Arrays.stream(s.split("\\|"))
                .map(row -> Arrays.stream(row.split(",")).mapToInt(Integer::parseInt).toArray())
                .toArray(int[][]::new);
    }
}
