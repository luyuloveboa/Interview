package rectangleboard.base;

import java.util.*;

public class RectangleBoard {
    List<int[]> rects = new ArrayList<>();          // rects.get(id - 1) = {x1, y1, x2, y2}; kept forever, ids never reused
    TreeSet<Integer> active = new TreeSet<>();      // active ids; larger id = added later = on top

    public RectangleBoard(int width, int height) {}

    public int addRectangle(int x1, int y1, int x2, int y2) {
        rects.add(new int[]{x1, y1, x2, y2});
        int id = rects.size();
        active.add(id);
        return id;
    }

    public boolean removeRectangle(int rectangleId) {
        return active.remove(rectangleId);
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
