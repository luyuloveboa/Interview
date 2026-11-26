package rectangleboard;

import java.util.*;
import testutil.Check;

public class RectangleBoardTest {
    public static void main(String[] args) { run(); }

    public static void run() {
        Check.section("Rectangle board: base");
        rectangleboard.base.RectangleBoard b0 = new rectangleboard.base.RectangleBoard(4, 3);
        Check.eq("ex", List.of(b0.addRectangle(1, 0, 2, 1), b0.addRectangle(2, 1, 3, 2), b0.getAt(2, 1),
                b0.removeRectangle(2), b0.getAt(2, 1), b0.getAt(3, 2), b0.removeRectangle(2)),
                List.of(1, 2, 2, true, 1, -1, false));
        Check.eq("unknown id", b0.removeRectangle(99), false);

        Check.section("Rectangle board: FU1 (undo)");
        rectangleboard.fu1.RectangleBoard b1 = new rectangleboard.fu1.RectangleBoard(4, 3);
        Check.eq("ex", List.of(b1.addRectangle(1, 0, 2, 1), b1.addRectangle(2, 1, 3, 2), b1.removeRectangle(1),
                b1.undo(), b1.getAt(2, 1), b1.undo(), b1.getAt(2, 1), b1.undo(), b1.undo()),
                List.of(1, 2, true, true, 2, true, 1, true, false));
        rectangleboard.fu1.RectangleBoard u = new rectangleboard.fu1.RectangleBoard(4, 3);
        u.addRectangle(0, 0, 2, 2);
        u.addRectangle(1, 1, 3, 2);
        u.undo();                                                  // must undo the MOST RECENT op (a stack, not a queue)
        Check.eq("undo is LIFO", u.getAt(1, 1), 1);
        Check.eq("ids keep increasing after undo", u.addRectangle(0, 0, 0, 0), 3);

        Check.section("Rectangle board: FU2 (removeAt)");
        rectangleboard.fu2.RectangleBoard b2 = new rectangleboard.fu2.RectangleBoard(4, 3);
        Check.eq("ex", List.of(b2.addRectangle(1, 0, 2, 1), b2.addRectangle(2, 1, 3, 2), b2.removeAt(2, 1),
                b2.getAt(2, 1), b2.undo(), b2.getAt(2, 1), b2.removeAt(0, 2)),
                List.of(1, 2, 2, 1, true, 2, -1));

        Check.section("Rectangle board: FU3 (batchRemove)");
        rectangleboard.fu3.RectangleBoard b3 = new rectangleboard.fu3.RectangleBoard(4, 3);
        Check.eq("ex", List.of(b3.addRectangle(1, 0, 2, 1), b3.addRectangle(2, 1, 3, 2), b3.batchRemove(2, 1),
                b3.getAt(2, 1), b3.batchRemove(2, 1), b3.undo(), b3.getAt(2, 1), b3.getAt(1, 0)),
                List.of(1, 2, 2, -1, 0, true, 2, 1));

        randomVsSnapshotReference();
    }

    /** Reference: before every change, save a full copy of the active set; undo restores the copy. */
    static void randomVsSnapshotReference() {
        Check.section("Rectangle board: random op sequences vs snapshot reference");
        Random rnd = new Random(5);
        int mismatches = 0;
        for (int it = 0; it < 2000; it++) {
            rectangleboard.fu3.RectangleBoard board = new rectangleboard.fu3.RectangleBoard(6, 6);
            TreeSet<Integer> ref = new TreeSet<>();
            Deque<TreeSet<Integer>> snaps = new ArrayDeque<>();
            List<int[]> rects = new ArrayList<>();
            for (int k = 0; k < 60; k++) {
                int op = rnd.nextInt(6), x = rnd.nextInt(6), y = rnd.nextInt(6);
                Object got, exp;
                switch (op) {
                    case 0: {
                        int x2 = x + rnd.nextInt(6 - x), y2 = y + rnd.nextInt(6 - y);
                        snaps.push(new TreeSet<>(ref));
                        rects.add(new int[]{x, y, x2, y2});
                        ref.add(rects.size());
                        exp = rects.size();
                        got = board.addRectangle(x, y, x2, y2);
                        break;
                    }
                    case 1: {
                        int id = rnd.nextInt(rects.size() + 2);
                        boolean ok = ref.contains(id);
                        if (ok) { snaps.push(new TreeSet<>(ref)); ref.remove(id); }
                        exp = ok;
                        got = board.removeRectangle(id);
                        break;
                    }
                    case 2: {
                        boolean ok = !snaps.isEmpty();
                        if (ok) ref = snaps.pop();
                        exp = ok;
                        got = board.undo();
                        break;
                    }
                    case 3: {
                        int top = top(ref, rects, x, y);
                        if (top != -1) { snaps.push(new TreeSet<>(ref)); ref.remove(top); }
                        exp = top;
                        got = board.removeAt(x, y);
                        break;
                    }
                    case 4: {
                        List<Integer> ids = new ArrayList<>();
                        for (int id : ref) if (covers(rects.get(id - 1), x, y)) ids.add(id);
                        if (!ids.isEmpty()) { snaps.push(new TreeSet<>(ref)); ref.removeAll(ids); }
                        exp = ids.size();
                        got = board.batchRemove(x, y);
                        break;
                    }
                    default: {
                        exp = top(ref, rects, x, y);
                        got = board.getAt(x, y);
                    }
                }
                if (!exp.equals(got)) mismatches++;
            }
        }
        Check.eq("mismatches", mismatches, 0);
    }

    static int top(TreeSet<Integer> ref, List<int[]> rects, int x, int y) {
        int top = -1;
        for (int id : ref) if (covers(rects.get(id - 1), x, y)) top = id;
        return top;
    }

    static boolean covers(int[] r, int x, int y) { return r[0] <= x && x <= r[2] && r[1] <= y && y <= r[3]; }
}
