package rectangleboard;

import java.util.*;
import java.util.function.Supplier;
import testutil.Check;

public class RectangleBoardTest {
    public static void main(String[] args) { run(); }

    public static void run() {
        base();
        fu1();
        fu2();
        fu3();
        randomVsSnapshotReference();
    }

    static void base() {
        Check.section("Rectangle board: base");
        rectangleboard.base.RectangleBoard b = new rectangleboard.base.RectangleBoard(4, 3);
        step("ex", "addRectangle(1,0,2,1)", () -> b.addRectangle(1, 0, 2, 1), 1);
        step("ex", "addRectangle(2,1,3,2)", () -> b.addRectangle(2, 1, 3, 2), 2);
        step("ex", "getAt(2,1)", () -> b.getAt(2, 1), 2);
        step("ex", "removeRectangle(2)", () -> b.removeRectangle(2), true);
        step("ex", "getAt(2,1)", () -> b.getAt(2, 1), 1);
        step("ex", "getAt(3,2)", () -> b.getAt(3, 2), -1);
        step("ex", "removeRectangle(2) again", () -> b.removeRectangle(2), false);
        step("unknown id", "removeRectangle(99)", () -> b.removeRectangle(99), false);
        step("id 0 never exists", "removeRectangle(0)", () -> b.removeRectangle(0), false);
        step("ids are never reused", "addRectangle(0,0,0,0)", () -> b.addRectangle(0, 0, 0, 0), 3);

        rectangleboard.base.RectangleBoard e = new rectangleboard.base.RectangleBoard(3, 3);
        step("empty board", "getAt(1,1)", () -> e.getAt(1, 1), -1);
        e.addRectangle(0, 0, 2, 2);
        e.addRectangle(1, 1, 1, 1);
        step("later rectangle on top", "getAt(1,1)", () -> e.getAt(1, 1), 2);
        step("lower rectangle visible elsewhere", "getAt(0,0)", () -> e.getAt(0, 0), 1);
        step("x2,y2 corner is inclusive", "getAt(2,2)", () -> e.getAt(2, 2), 1);
        step("remove bottom rectangle", "removeRectangle(1)", () -> e.removeRectangle(1), true);
        step("top one unaffected", "getAt(1,1)", () -> e.getAt(1, 1), 2);
        step("bottom one gone", "getAt(0,0)", () -> e.getAt(0, 0), -1);
    }

    static void fu1() {
        Check.section("Rectangle board: FU1 (undo)");
        rectangleboard.fu1.RectangleBoard b = new rectangleboard.fu1.RectangleBoard(4, 3);
        step("ex", "addRectangle(1,0,2,1)", () -> b.addRectangle(1, 0, 2, 1), 1);
        step("ex", "addRectangle(2,1,3,2)", () -> b.addRectangle(2, 1, 3, 2), 2);
        step("ex", "removeRectangle(1)", () -> b.removeRectangle(1), true);
        step("ex", "undo() the remove", () -> b.undo(), true);
        step("ex", "getAt(2,1)", () -> b.getAt(2, 1), 2);
        step("ex", "undo() add #2", () -> b.undo(), true);
        step("ex", "getAt(2,1)", () -> b.getAt(2, 1), 1);
        step("ex", "undo() add #1", () -> b.undo(), true);
        step("ex", "undo() with empty history", () -> b.undo(), false);

        rectangleboard.fu1.RectangleBoard u = new rectangleboard.fu1.RectangleBoard(4, 3);
        u.addRectangle(0, 0, 2, 2);
        u.addRectangle(1, 1, 3, 2);
        u.undo();                                                  // must undo the MOST RECENT op (a stack, not a queue)
        step("undo is LIFO", "getAt(1,1)", () -> u.getAt(1, 1), 1);
        step("ids keep increasing after undo", "addRectangle(0,0,0,0)", () -> u.addRectangle(0, 0, 0, 0), 3);

        rectangleboard.fu1.RectangleBoard f = new rectangleboard.fu1.RectangleBoard(3, 3);
        step("fresh board", "undo()", () -> f.undo(), false);
        f.addRectangle(0, 0, 1, 1);
        step("failed remove", "removeRectangle(5)", () -> f.removeRectangle(5), false);
        step("failed remove is not in history", "undo() undoes the add", () -> f.undo(), true);
        step("failed remove is not in history", "getAt(0,0)", () -> f.getAt(0, 0), -1);
        step("failed remove is not in history", "undo() again", () -> f.undo(), false);

        rectangleboard.fu1.RectangleBoard l = new rectangleboard.fu1.RectangleBoard(3, 3);
        l.addRectangle(0, 0, 2, 2);
        l.addRectangle(0, 0, 2, 2);
        l.removeRectangle(1);
        l.undo();                                                  // #1 comes back UNDER #2
        step("restored rectangle keeps its layer", "getAt(1,1)", () -> l.getAt(1, 1), 2);
        step("restored rectangle keeps its layer", "removeRectangle(2)", () -> l.removeRectangle(2), true);
        step("restored rectangle keeps its layer", "getAt(1,1)", () -> l.getAt(1, 1), 1);
    }

    static void fu2() {
        Check.section("Rectangle board: FU2 (removeAt)");
        rectangleboard.fu2.RectangleBoard b = new rectangleboard.fu2.RectangleBoard(4, 3);
        step("ex", "addRectangle(1,0,2,1)", () -> b.addRectangle(1, 0, 2, 1), 1);
        step("ex", "addRectangle(2,1,3,2)", () -> b.addRectangle(2, 1, 3, 2), 2);
        step("ex", "removeAt(2,1)", () -> b.removeAt(2, 1), 2);
        step("ex", "getAt(2,1)", () -> b.getAt(2, 1), 1);
        step("ex", "undo() the removeAt", () -> b.undo(), true);
        step("ex", "getAt(2,1)", () -> b.getAt(2, 1), 2);
        step("ex", "removeAt(0,2) on empty cell", () -> b.removeAt(0, 2), -1);

        rectangleboard.fu2.RectangleBoard s = new rectangleboard.fu2.RectangleBoard(3, 3);
        s.addRectangle(0, 0, 2, 2);
        s.addRectangle(1, 1, 2, 2);
        step("removes top first", "removeAt(1,1)", () -> s.removeAt(1, 1), 2);
        step("then the one below", "removeAt(1,1)", () -> s.removeAt(1, 1), 1);
        step("then nothing left", "removeAt(1,1)", () -> s.removeAt(1, 1), -1);
        step("failed removeAt is not in history", "undo() restores #1", () -> s.undo(), true);
        step("failed removeAt is not in history", "getAt(1,1)", () -> s.getAt(1, 1), 1);
        step("undo again restores #2", "undo()", () -> s.undo(), true);
        step("undo again restores #2", "getAt(1,1)", () -> s.getAt(1, 1), 2);
        step("removeRectangle still works", "removeRectangle(1)", () -> s.removeRectangle(1), true);
        step("removeRectangle still works", "getAt(0,0)", () -> s.getAt(0, 0), -1);
    }

    static void fu3() {
        Check.section("Rectangle board: FU3 (batchRemove)");
        rectangleboard.fu3.RectangleBoard b = new rectangleboard.fu3.RectangleBoard(4, 3);
        step("ex", "addRectangle(1,0,2,1)", () -> b.addRectangle(1, 0, 2, 1), 1);
        step("ex", "addRectangle(2,1,3,2)", () -> b.addRectangle(2, 1, 3, 2), 2);
        step("ex", "batchRemove(2,1)", () -> b.batchRemove(2, 1), 2);
        step("ex", "getAt(2,1)", () -> b.getAt(2, 1), -1);
        step("ex", "batchRemove(2,1) again", () -> b.batchRemove(2, 1), 0);
        step("ex", "undo() restores both at once", () -> b.undo(), true);
        step("ex", "getAt(2,1)", () -> b.getAt(2, 1), 2);
        step("ex", "getAt(1,0)", () -> b.getAt(1, 0), 1);

        rectangleboard.fu3.RectangleBoard c = new rectangleboard.fu3.RectangleBoard(4, 4);
        c.addRectangle(0, 0, 1, 1);
        c.addRectangle(2, 2, 3, 3);
        c.addRectangle(0, 0, 3, 3);
        step("only covering rectangles removed", "batchRemove(0,0)", () -> c.batchRemove(0, 0), 2);
        step("non-covering rectangle survives", "getAt(3,3)", () -> c.getAt(3, 3), 2);
        step("covered cell now empty", "getAt(1,1)", () -> c.getAt(1, 1), -1);
        step("empty batchRemove", "batchRemove(1,0)", () -> c.batchRemove(1, 0), 0);
        step("empty batchRemove is not in history", "undo()", () -> c.undo(), true);
        step("undo restores top layer", "getAt(0,0)", () -> c.getAt(0, 0), 3);
        step("undo restores top layer", "getAt(3,3)", () -> c.getAt(3, 3), 3);
        step("then undo the last add", "undo()", () -> c.undo(), true);
        step("then undo the last add", "getAt(0,0)", () -> c.getAt(0, 0), 1);
        step("removeAt mixed in", "removeAt(3,3)", () -> c.removeAt(3, 3), 2);
        step("removeAt mixed in", "undo()", () -> c.undo(), true);
        step("removeAt mixed in", "getAt(3,3)", () -> c.getAt(3, 3), 2);
    }

    static void step(String scenario, String call, Supplier<?> got, Object expected) {
        Check.eq(scenario + ": " + call, got, expected);
    }

    /** Reference: before every change, save a full copy of the active set; undo restores the copy. */
    static void randomVsSnapshotReference() {
        Check.section("Rectangle board: random op sequences vs snapshot reference");
        Random rnd = new Random(5);
        int badSequences = 0, sequences = 2000;
        String first = null;
        for (int it = 0; it < sequences; it++) {
            rectangleboard.fu3.RectangleBoard board = new rectangleboard.fu3.RectangleBoard(6, 6);
            TreeSet<Integer> ref = new TreeSet<>();
            Deque<TreeSet<Integer>> snaps = new ArrayDeque<>();
            List<int[]> rects = new ArrayList<>();
            List<String> log = new ArrayList<>();
            for (int k = 0; k < 60; k++) {
                int op = rnd.nextInt(6), x = rnd.nextInt(6), y = rnd.nextInt(6);
                Object exp;
                String call;
                Supplier<?> act;
                switch (op) {
                    case 0: {
                        int x2 = x + rnd.nextInt(6 - x), y2 = y + rnd.nextInt(6 - y);
                        snaps.push(new TreeSet<>(ref));
                        rects.add(new int[]{x, y, x2, y2});
                        ref.add(rects.size());
                        exp = rects.size();
                        call = "addRectangle(" + x + "," + y + "," + x2 + "," + y2 + ")";
                        act = () -> board.addRectangle(x, y, x2, y2);
                        break;
                    }
                    case 1: {
                        int id = rnd.nextInt(rects.size() + 2);
                        boolean ok = ref.contains(id);
                        if (ok) { snaps.push(new TreeSet<>(ref)); ref.remove(id); }
                        exp = ok;
                        call = "removeRectangle(" + id + ")";
                        act = () -> board.removeRectangle(id);
                        break;
                    }
                    case 2: {
                        boolean ok = !snaps.isEmpty();
                        if (ok) ref = snaps.pop();
                        exp = ok;
                        call = "undo()";
                        act = board::undo;
                        break;
                    }
                    case 3: {
                        int top = top(ref, rects, x, y);
                        if (top != -1) { snaps.push(new TreeSet<>(ref)); ref.remove(top); }
                        exp = top;
                        call = "removeAt(" + x + "," + y + ")";
                        act = () -> board.removeAt(x, y);
                        break;
                    }
                    case 4: {
                        List<Integer> ids = new ArrayList<>();
                        for (int id : ref) if (covers(rects.get(id - 1), x, y)) ids.add(id);
                        if (!ids.isEmpty()) { snaps.push(new TreeSet<>(ref)); ref.removeAll(ids); }
                        exp = ids.size();
                        call = "batchRemove(" + x + "," + y + ")";
                        act = () -> board.batchRemove(x, y);
                        break;
                    }
                    default: {
                        exp = top(ref, rects, x, y);
                        call = "getAt(" + x + "," + y + ")";
                        act = () -> board.getAt(x, y);
                    }
                }
                Check.Outcome got = Check.attempt(act);
                if (!got.matches(exp)) {
                    // state has diverged, so later ops in this sequence would just be noise
                    StringBuilder sb = new StringBuilder("new RectangleBoard(6, 6), then:\n");
                    for (String line : log) sb.append("  ").append(line).append('\n');
                    sb.append("  ").append(call).append(" -> expected ").append(Check.show(exp))
                      .append(", got ").append(got).append("   <-- first wrong answer\n");
                    sb.append("active ids per reference after this op: ").append(ref);
                    if (badSequences++ == 0) first = sb.toString();
                    if (got.timedOut) {
                        Check.fail("random op sequences", sb.toString());
                        throw Check.abort();
                    }
                    break;
                }
                log.add(call + " -> " + got);
            }
        }
        String name = "random op sequences";
        if (badSequences == 0) Check.eq(name, 0, 0);
        else Check.fail(name, badSequences + " of " + sequences + " random sequences went wrong. First one:\n" + first);
    }

    static int top(TreeSet<Integer> ref, List<int[]> rects, int x, int y) {
        int top = -1;
        for (int id : ref) if (covers(rects.get(id - 1), x, y)) top = id;
        return top;
    }

    static boolean covers(int[] r, int x, int y) { return r[0] <= x && x <= r[2] && r[1] <= y && y <= r[3]; }
}
