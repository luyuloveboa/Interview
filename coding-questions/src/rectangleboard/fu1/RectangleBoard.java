package rectangleboard.fu1;

import java.util.*;

public class RectangleBoard {
    List<int[]> rects = new ArrayList<>();          // rects.get(id - 1) = {x1, y1, x2, y2}; kept forever, ids never reused
    TreeSet<Integer> active = new TreeSet<>();      // active ids; larger id = added later = on top
    Deque<List<Integer>> history = new ArrayDeque<>();   // [FU1 NEW] ids changed by each successful op

    public RectangleBoard(int width, int height) {}

    public int addRectangle(int x1, int y1, int x2, int y2) {
        rects.add(new int[]{x1, y1, x2, y2});
        int id = rects.size();
        active.add(id);
        history.push(List.of(id));                  // [FU1 NEW]
        return id;
    }

    public boolean removeRectangle(int rectangleId) {
        if (!active.remove(rectangleId)) return false;   // [FU1 CHANGED] failed removal: no history
        history.push(List.of(rectangleId));              // [FU1 NEW]
        return true;
    }

    // [FU1 NEW] Undo flips every id of the last op. Because history is a stack, each id is still
    //           exactly as that op left it. Overlap order comes back for free: it is just the id order.
    public boolean undo() {
        if (history.isEmpty()) return false;
        for (int id : history.pop()) {
            if (active.contains(id)) active.remove(id);  // undo an add
            else active.add(id);                         // undo a remove
        }
        return true;
    }

    public int getAt(int x, int y) {
        for (int id : active.descendingSet())       // from top to bottom
            if (covers(id, x, y)) return id;
        return -1;
    }

    boolean covers(int id, int x, int y) {
        int[] r = rects.get(id - 1);
        return r[0] <= x && x <= r[2] && r[1] <= y && y <= r[3];
    }
}
