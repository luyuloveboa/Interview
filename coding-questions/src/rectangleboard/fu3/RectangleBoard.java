package rectangleboard.fu3;

import java.util.*;

public class RectangleBoard {
    List<int[]> rects = new ArrayList<>();          // rects.get(id - 1) = {x1, y1, x2, y2}; kept forever, ids never reused
    TreeSet<Integer> active = new TreeSet<>();      // active ids; larger id = added later = on top
    Deque<List<Integer>> history = new ArrayDeque<>();   // ids changed by each successful op

    public RectangleBoard(int width, int height) {}

    public int addRectangle(int x1, int y1, int x2, int y2) {
        rects.add(new int[]{x1, y1, x2, y2});
        int id = rects.size();
        active.add(id);
        history.push(List.of(id));
        return id;
    }

    public boolean removeRectangle(int rectangleId) {
        if (!active.remove(rectangleId)) return false;   // failed removal: no history
        history.push(List.of(rectangleId));
        return true;
    }

    // Undo flips every id of the last op (stack order: each id is still as that op left it).
    // Overlap order comes back for free: it is just the id order.
    public boolean undo() {
        if (history.isEmpty()) return false;
        for (int id : history.pop()) {
            if (active.contains(id)) active.remove(id);  // undo an add
            else active.add(id);                         // undo a remove
        }
        return true;
    }

    // reuses getAt + removeRectangle, so history is handled automatically
    public int removeAt(int x, int y) {
        int id = getAt(x, y);
        if (id != -1) removeRectangle(id);
        return id;
    }

    // [FU3 NEW] Remove every active rectangle covering (x, y) as ONE history entry.
    //           undo() already flips a whole list of ids, so it needs no change.
    public int batchRemove(int x, int y) {
        List<Integer> ids = new ArrayList<>();
        for (int id : active) if (covers(id, x, y)) ids.add(id);
        if (ids.isEmpty()) return 0;
        active.removeAll(ids);
        history.push(ids);
        return ids.size();
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
