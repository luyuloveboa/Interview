package plantdisease;

import java.util.*;
import java.util.function.Supplier;
import testutil.Check;

public class PlantDiseaseTest {
    public static void main(String[] args) { run(); }

    public static void run() {
        Check.section("Plant disease: base");
        base("ex1", "1,0,0|0,0,0|0,0,0", 1, 2);
        base("ex2", "1,0|0,0", 2, 0);
        base("ex3", "1,1,0|0,0,0|0,0,0", 2, 3);
        base("no infected at all", "0,0|0,0", 1, 0);
        base("single infected cell", "1", 1, 0);
        base("spreads one cell per day along a row", "1,0,0,0,0", 1, 4);
        base("center infects all 8 neighbors in one day", "0,0,0|0,1,0|0,0,0", 1, 1);
        base("two sources together reach threshold", "1,0,1", 2, 1);
        base("two sources too far apart", "1,0,0,1", 2, 0);
        base("threshold 8 met by a full ring", "1,1,1|1,0,1|1,1,1", 8, 1);
        base("threshold 9 can never be met", "1,1,1|1,0,1|1,1,1", 9, 0);
        base("spreads down a column", "1|0|0|0", 1, 3);

        Check.section("Plant disease: FU1 (immune)");
        fu1("ex1", "1,0,0|0,2,0|0,0,0", 1, 3);
        fu1("ex2", "1,1|2,0", 2, 1);
        fu1("ex3", "1,2,0|2,2,0|0,0,0", 2, 0);
        fu1("immune cell blocks a 1-wide row", "1,2,0", 1, 0);
        fu1("diagonal goes around an immune cell", "1,0|2,0", 1, 1);
        fu1("all immune", "2,2|2,2", 1, 0);
        fu1("spread routes around an immune column", "1,2,0|0,2,0|0,0,0", 1, 4);
        fu1("immune cells do not count as infected", "1,2,1|0,0,0", 3, 0);
        fu1("no immune cells behaves like base", "1,0,0,0,0", 1, 4);

        Check.section("Plant disease: FU2 (recovery)");
        fu2("ex1", "1,0,0|0,0,0|0,0,0", 1, 2, 4);
        fu2("ex2", "1,0", 1, 1, 2);
        fu2("ex3", "1,2|2,0", 2, 3, 3);
        // recovered cells must stop counting (needs the -1 delta)
        fu2("recovered stop counting", "0,0,0|0,1,1", 2, 1, 2);
        // recovered cells must never be reinfected (checking the original grid instead of g loops forever)
        fu2("no reinfection", "0,0,1", 1, 1, 3);
        fu2("lone seed only recovers", "1", 1, 3, 3);
        fu2("wave along a row, fast recovery", "1,0,0,0,0", 1, 1, 5);
        fu2("wave along a row, slow recovery", "1,0,0,0,0", 1, 5, 9);
        fu2("sources recover the same day they infect", "1,0,1", 2, 1, 2);
        fu2("nothing infected", "0,0|0,0", 1, 4, 0);

        Check.section("Plant disease: FU3 (death)");
        fu3("ex1", "1,0,0|0,0,0|0,0,0", 1, 1, 1, new int[]{3, 8});
        fu3("ex2", "1,0,0|0,2,0|0,0,0", 1, 2, 8, new int[]{5, 0});
        fu3("ex3", "1,0,0,0|0,0,0,0|0,0,0,0|0,0,0,0", 1, 3, 3, new int[]{6, 2});
        fu3("lone seed is never fatal", "1", 1, 1, 1, new int[]{1, 0});
        fu3("adjacent seeds are fatal from the start", "1,1", 1, 1, 1, new int[]{1, 2});
        fu3("below death threshold everyone recovers", "1,0", 1, 2, 2, new int[]{3, 0});
        fu3("cell infected by two neighbors dies", "1,0,1", 1, 1, 2, new int[]{2, 1});
        fu3("nothing infected", "0,0|0,0", 1, 1, 1, new int[]{0, 0});

        Check.section("Plant disease: FU4 (firebreak)");
        fu4("ex1", "1,0,0|0,0,0|0,0,1", 1, 1, 1, 4);
        fu4("ex2", "1,0,0|0,0,0|0,0,0", 1, 1, 1, 0);
        fu4("ex3", "1,1,0,0|1,0,2,0|0,2,0,1|0,0,1,1", 2, 2, 2, 5);
        fu4("burning the seeds' row saves everyone", "1,1", 1, 1, 1, 0);
        fu4("nothing infected", "0,0|0,0", 1, 1, 1, 0);
        fu4("burning the middle column separates the seeds", "1,0,1", 1, 1, 2, 0);
        fu4("2x2 block: best burn still leaves 2 deaths", "1,1|1,1", 1, 1, 1, 2);

        randomVsBruteForce();
    }

    static void base(String name, String grid, int t, int expected) {
        Check.eq(name + ": daysToStabilize(" + q(grid) + ", " + t + ")",
                () -> new plantdisease.base.Solution().daysToStabilize(g(grid), t), expected);
    }

    static void fu1(String name, String grid, int t, int expected) {
        Check.eq(name + ": daysToStabilize(" + q(grid) + ", " + t + ")",
                () -> new plantdisease.fu1.Solution().daysToStabilize(g(grid), t), expected);
    }

    static void fu2(String name, String grid, int t, int rd, int expected) {
        Check.eq(name + ": daysToStabilize(" + q(grid) + ", " + t + ", " + rd + ")",
                () -> new plantdisease.fu2.Solution().daysToStabilize(g(grid), t, rd), expected);
    }

    static void fu3(String name, String grid, int t, int rd, int dt, int[] expected) {
        Check.eq(name + ": epidemicWithDeath(" + q(grid) + ", " + t + ", " + rd + ", " + dt + ")",
                () -> new plantdisease.fu3.Solution().epidemicWithDeath(g(grid), t, rd, dt), expected);
    }

    static void fu4(String name, String grid, int t, int rd, int dt, int expected) {
        Check.eq(name + ": minDeathsWithFirebreak(" + q(grid) + ", " + t + ", " + rd + ", " + dt + ")",
                () -> new plantdisease.fu4.Solution().minDeathsWithFirebreak(g(grid), t, rd, dt), expected);
    }

    static String q(String grid) { return "g(\"" + grid + "\")"; }

    static final String[] VERSIONS = {"base", "FU1", "FU2", "FU3", "FU4"};

    /** Compares every version against a slow, obviously-correct day-by-day simulation. */
    static void randomVsBruteForce() {
        Check.section("Plant disease: random grids vs brute force");
        Random rnd = new Random(42);
        int[] mismatches = new int[5], cases = new int[5];
        String[] first = new String[5];
        for (int it = 0; it < 3000; it++) {
            int[][] grid = randomGrid(rnd);
            int t = 1 + rnd.nextInt(4), rd = 1 + rnd.nextInt(4), dt = 1 + rnd.nextInt(8);
            int[][] plain = noImmune(grid);
            String a1 = ", " + t, a2 = a1 + ", " + rd, a3 = a2 + ", " + dt;
            compare(0, mismatches, cases, first, "daysToStabilize(" + q(str(plain)) + a1 + ")",
                    () -> new plantdisease.base.Solution().daysToStabilize(copy(plain), t), brute(plain, t, 0, 99)[0]);
            compare(1, mismatches, cases, first, "daysToStabilize(" + q(str(grid)) + a1 + ")",
                    () -> new plantdisease.fu1.Solution().daysToStabilize(copy(grid), t), brute(grid, t, 0, 99)[0]);
            compare(2, mismatches, cases, first, "daysToStabilize(" + q(str(grid)) + a2 + ")",
                    () -> new plantdisease.fu2.Solution().daysToStabilize(copy(grid), t, rd), brute(grid, t, rd, 99)[0]);
            compare(3, mismatches, cases, first, "epidemicWithDeath(" + q(str(grid)) + a3 + ")",
                    () -> new plantdisease.fu3.Solution().epidemicWithDeath(copy(grid), t, rd, dt), brute(grid, t, rd, dt));
            if (it < 800)
                compare(4, mismatches, cases, first, "minDeathsWithFirebreak(" + q(str(grid)) + a3 + ")",
                        () -> new plantdisease.fu4.Solution().minDeathsWithFirebreak(copy(grid), t, rd, dt), bruteFirebreak(grid, t, rd, dt));
        }
        for (int v = 0; v < 5; v++) {
            String name = VERSIONS[v] + ": random grids";
            if (mismatches[v] == 0) Check.eq(name, 0, 0);
            else Check.fail(name, mismatches[v] + " of " + cases[v] + " random grids differ from brute force. First one:\n" + first[v]);
        }
    }

    static void compare(int v, int[] mismatches, int[] cases, String[] first, String call, Supplier<?> got, Object expected) {
        cases[v]++;
        Check.Outcome o = Check.attempt(got);
        if (o.matches(expected)) return;
        String detail = call + "\n  expected: " + Check.show(expected) + "\n  got:      " + o;
        if (o.timedOut) {
            Check.fail(VERSIONS[v] + ": random grids", detail);
            throw Check.abort();
        }
        if (mismatches[v]++ == 0) first[v] = detail;
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

    /** {{1,0},{0,0}} -> "1,0|0,0", the format g() reads, so a failing case can be pasted into a test */
    static String str(int[][] grid) {
        StringJoiner rows = new StringJoiner("|");
        for (int[] row : grid) {
            StringJoiner cells = new StringJoiner(",");
            for (int v : row) cells.add(String.valueOf(v));
            rows.add(cells.toString());
        }
        return rows.toString();
    }
}
