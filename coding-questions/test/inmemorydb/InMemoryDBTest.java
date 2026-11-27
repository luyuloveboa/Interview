package inmemorydb;

import java.lang.reflect.Method;
import java.util.*;
import testutil.Check;

public class InMemoryDBTest {
    public static void main(String[] args) { run(); }

    public static void run() {
        base();
        fu1();
        fu2();
        fu3();
        randomVsReference();
    }

    static void base() {
        Check.section("In-memory DB: base");
        steps("ex", new inmemorydb.base.InMemoryDB(), new Object[][]{
                {"setData", "A", "B", "E", null},
                {"setData", "A", "C", "F", null},
                {"getData", "A", "B", "E"},
                {"getData", "A", "D", ""},
                {"deleteData", "A", "B", true},
                {"deleteData", "A", "D", false},
        });
        steps("edge", new inmemorydb.base.InMemoryDB(), new Object[][]{
                {"getData", "X", "f", ""},                          // empty DB
                {"deleteData", "X", "f", false},
                {"setData", "K", "f", "v1", null},
                {"setData", "K", "f", "v2", null},                  // overwrite
                {"getData", "K", "f", "v2"},
                {"getData", "k", "f", ""},                          // keys are case-sensitive
                {"setData", "L", "f", "other", null},
                {"getData", "K", "f", "v2"},                        // records don't share fields
                {"deleteData", "K", "f", true},
                {"deleteData", "K", "f", false},                    // already gone
                {"getData", "K", "f", ""},
                {"setData", "K", "g", "w", null},                   // record re-created after it became empty
                {"getData", "K", "g", "w"},
                {"getData", "L", "f", "other"},
        });
    }

    static void fu1() {
        Check.section("In-memory DB: FU1 (scan)");
        steps("ex", new inmemorydb.fu1.InMemoryDB(), new Object[][]{
                {"setData", "A", "BC", "E", null},
                {"setData", "A", "BD", "F", null},
                {"setData", "A", "C", "G", null},
                {"scanDataByPrefix", "A", "B", List.of("BC(E)", "BD(F)")},
                {"scanData", "A", List.of("BC(E)", "BD(F)", "C(G)")},
                {"scanDataByPrefix", "B", "B", List.of()},
        });
        steps("edge", new inmemorydb.fu1.InMemoryDB(), new Object[][]{
                {"scanData", "R", List.of()},                       // missing record
                {"setData", "R", "b", "1", null},
                {"setData", "R", "B", "2", null},
                {"setData", "R", "ab", "3", null},
                {"setData", "R", "a", "4", null},
                {"scanData", "R", List.of("B(2)", "a(4)", "ab(3)", "b(1)")},   // ASCII order: upper before lower
                {"scanDataByPrefix", "R", "", List.of("B(2)", "a(4)", "ab(3)", "b(1)")},
                {"scanDataByPrefix", "R", "a", List.of("a(4)", "ab(3)")},       // exact match is included
                {"scanDataByPrefix", "R", "ab", List.of("ab(3)")},
                {"scanDataByPrefix", "R", "abc", List.of()},        // prefix longer than every field
                {"scanDataByPrefix", "R", "A", List.of()},          // sorts between "B" and "a", matches nothing
                {"setData", "R", "a", "5", null},
                {"scanDataByPrefix", "R", "a", List.of("a(5)", "ab(3)")},       // overwrite shows the new value
                {"deleteData", "R", "ab", true},
                {"scanDataByPrefix", "R", "a", List.of("a(5)")},
                {"getData", "R", "b", "1"},                         // base methods still work
        });
    }

    static void fu2() {
        Check.section("In-memory DB: FU2 (timestamps + TTL)");
        steps("ex1", new inmemorydb.fu2.InMemoryDB(), new Object[][]{
                {"setDataAtWithTtl", "A", "BC", "E", 1, 9, null},
                {"setDataAtWithTtl", "A", "BC", "E", 5, 10, null},
                {"setDataAt", "A", "BD", "F", 5, null},
                {"scanDataByPrefixAt", "A", "", 14, List.of("BC(E)", "BD(F)")},
                {"scanDataByPrefixAt", "A", "", 15, List.of("BD(F)")},
        });
        steps("ex2", new inmemorydb.fu2.InMemoryDB(), new Object[][]{
                {"setDataAt", "A", "B", "C", 1, null},
                {"setDataAtWithTtl", "X", "Y", "Z", 2, 15, null},
                {"getDataAt", "X", "Y", 3, "Z"},
                {"setDataAtWithTtl", "A", "D", "E", 4, 10, null},
                {"scanDataAt", "A", 13, List.of("B(C)", "D(E)")},
                {"scanDataAt", "X", 16, List.of("Y(Z)")},
                {"scanDataAt", "X", 17, List.of()},
                {"deleteDataAt", "X", "Y", 20, false},
        });
        steps("edge", new inmemorydb.fu2.InMemoryDB(), new Object[][]{
                {"setDataAtWithTtl", "K", "f", "v", 1, 5, null},    // alive [1, 6)
                {"getDataAt", "K", "f", 5, "v"},                    // last moment
                {"getDataAt", "K", "f", 6, ""},                     // expiry is exclusive
                {"deleteDataAt", "K", "f", 7, false},               // expired = missing
                {"setDataAtWithTtl", "K", "g", "v", 8, 3, null},
                {"setDataAt", "K", "g", "w", 9, null},              // plain set clears the TTL
                {"getDataAt", "K", "g", 1000, "w"},
                {"setDataAtWithTtl", "K", "h", "x", 1001, 100, null},
                {"setDataAtWithTtl", "K", "h", "y", 1002, 1, null}, // a new TTL can also shorten the life
                {"getDataAt", "K", "h", 1003, ""},
                {"setDataAtWithTtl", "K", "i", "z", 1004, 2, null},
                {"deleteDataAt", "K", "i", 1005, true},
                {"deleteDataAt", "K", "i", 1005 + 1, false},
                {"setDataAtWithTtl", "P", "ab", "1", 1010, 5, null},
                {"setDataAt", "P", "ac", "2", 1011, null},
                {"scanDataByPrefixAt", "P", "a", 1014, List.of("ab(1)", "ac(2)")},
                {"scanDataByPrefixAt", "P", "a", 1015, List.of("ac(2)")},
                {"setDataAt", "P", "ab", "3", 1016, null},          // an expired field can be set again
                {"scanDataAt", "P", 1017, List.of("ab(3)", "ac(2)")},
        });
        steps("old API on the FU2 class", new inmemorydb.fu2.InMemoryDB(), new Object[][]{
                {"setData", "A", "BC", "E", null},
                {"setData", "A", "BD", "F", null},
                {"getData", "A", "BC", "E"},
                {"scanDataByPrefix", "A", "B", List.of("BC(E)", "BD(F)")},
                {"deleteData", "A", "BC", true},
                {"scanData", "A", List.of("BD(F)")},
        });
    }

    static void fu3() {
        Check.section("In-memory DB: FU3 (backup + restore)");
        steps("ex", new inmemorydb.fu3.InMemoryDB(), new Object[][]{
                // the problem's example calls this "setAtWithTtl"
                {"setDataAtWithTtl", "A", "B", "C", 1, 10, null},
                {"backup", 3, 1},
                {"setDataAt", "A", "D", "E", 4, null},
                {"backup", 5, 1},
                {"deleteDataAt", "A", "B", 8, true},
                {"backup", 9, 1},
                {"restore", 10, 7, null},                           // backup at 5: B had 6 left -> expires 16
                {"backup", 11, 1},
                {"scanDataAt", "A", 15, List.of("B(C)", "D(E)")},
                {"scanDataAt", "A", 16, List.of("D(E)")},
        });
        steps("edge", new inmemorydb.fu3.InMemoryDB(), new Object[][]{
                {"backup", 1, 0},                                   // empty DB
                {"setDataAtWithTtl", "A", "f", "v", 2, 3, null},    // alive [2, 5)
                {"setDataAt", "B", "g", "w", 3, null},
                {"setDataAt", "C", "h", "x", 3 + 1, null},
                {"deleteDataAt", "C", "h", 5 + 1, true},            // C is now empty
                {"backup", 7, 1},                                   // A expired, C empty: only B counts
                {"setDataAtWithTtl", "A", "f", "v2", 8, 10, null},  // alive [8, 18); 6 left at 12
                {"backup", 12, 2},
                {"setDataAt", "D", "z", "z", 13, null},
                {"restore", 20, 12, null},                          // exact match on the backup time
                {"getDataAt", "D", "z", 21, ""},                    // written after the backup: gone
                {"getDataAt", "A", "f", 25, "v2"},                  // 20 + 6 = 26
                {"getDataAt", "A", "f", 26, ""},
                {"getDataAt", "B", "g", 10_000, "w"},               // no TTL stays no TTL
                {"restore", 10_001, 11, null},                      // latest backup at or before 11 is 7
                {"getDataAt", "A", "f", 10_002, ""},
                {"getDataAt", "B", "g", 10_003, "w"},
                {"restore", 10_004, 12, null},                      // backups survive restores and later writes
                {"getDataAt", "A", "f", 10_009, "v2"},              // 10004 + 6 = 10010
                {"getDataAt", "A", "f", 10_010, ""},
                {"backup", 10_011, 1},
        });
    }

    /** Runs each step {method, args..., expected} as its own check. */
    static void steps(String label, Object db, Object[][] steps) {
        for (Object[] s : steps) {
            Object[] args = Arrays.copyOfRange(s, 1, s.length - 1);
            Check.eq(label + ": " + s[0] + Arrays.toString(args), () -> call(db, (String) s[0], args), s[s.length - 1]);
        }
    }

    static Object call(Object target, String method, Object[] args) {
        for (Method m : target.getClass().getMethods()) {
            if (m.getName().equals(method) && m.getParameterCount() == args.length) {
                try {
                    return m.invoke(target, args);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw e.getCause() instanceof RuntimeException ? (RuntimeException) e.getCause() : new RuntimeException(e.getCause());
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        throw new IllegalArgumentException("no method " + method + "/" + args.length + " on " + target.getClass().getName());
    }

    // ------------------------------------------------------------------------------------------
    // Random operation streams vs a deliberately naive reference: one flat list of rows scanned
    // linearly, absolute expiry times, and backups as full copies shifted by (restore - backup)
    // time on restore. It shares no data structures or helpers with the solutions.
    // ------------------------------------------------------------------------------------------

    public static class Ref {
        static final long NEVER = Long.MAX_VALUE;
        List<Object[]> rows = new ArrayList<>();                    // {key, field, value, expireAt}
        final List<Object[]> backups = new ArrayList<>();           // {time, rows}

        boolean alive(Object[] r, int t) { return t < (Long) r[3]; }

        Object[] find(String k, String f, int t) {
            for (Object[] r : rows) if (r[0].equals(k) && r[1].equals(f) && alive(r, t)) return r;
            return null;
        }

        void put(String k, String f, String v, long exp) {
            rows.removeIf(r -> r[0].equals(k) && r[1].equals(f));
            rows.add(new Object[]{k, f, v, exp});
        }

        public void setData(String k, String f, String v) { put(k, f, v, NEVER); }
        public String getData(String k, String f) { return getDataAt(k, f, 0); }
        public boolean deleteData(String k, String f) { return deleteDataAt(k, f, 0); }
        public List<String> scanData(String k) { return scanDataByPrefixAt(k, "", 0); }
        public List<String> scanDataByPrefix(String k, String p) { return scanDataByPrefixAt(k, p, 0); }

        public void setDataAt(String k, String f, String v, int t) { put(k, f, v, NEVER); }
        public void setDataAtWithTtl(String k, String f, String v, int t, int ttl) { put(k, f, v, (long) t + ttl); }

        public String getDataAt(String k, String f, int t) {
            Object[] r = find(k, f, t);
            return r == null ? "" : (String) r[2];
        }

        public boolean deleteDataAt(String k, String f, int t) {
            Object[] r = find(k, f, t);
            return r != null && rows.remove(r);
        }

        public List<String> scanDataAt(String k, int t) { return scanDataByPrefixAt(k, "", t); }

        public List<String> scanDataByPrefixAt(String k, String p, int t) {
            List<Object[]> hit = new ArrayList<>();
            for (Object[] r : rows) if (r[0].equals(k) && ((String) r[1]).startsWith(p) && alive(r, t)) hit.add(r);
            hit.sort(Comparator.comparing(r -> (String) r[1]));
            List<String> out = new ArrayList<>();
            for (Object[] r : hit) out.add(r[1] + "(" + r[2] + ")");
            return out;
        }

        public int backup(int t) {
            List<Object[]> copy = new ArrayList<>();
            Set<Object> keys = new HashSet<>();
            for (Object[] r : rows) if (alive(r, t)) { copy.add(r.clone()); keys.add(r[0]); }
            backups.add(new Object[]{t, copy});
            return keys.size();
        }

        @SuppressWarnings("unchecked")
        public void restore(int t, int toRestore) {
            Object[] best = null;
            for (Object[] b : backups) if ((Integer) b[0] <= toRestore) best = b;   // added in time order
            if (best == null) return;
            long shift = t - (Integer) best[0];
            rows = new ArrayList<>();
            for (Object[] r : (List<Object[]>) best[1]) {
                long exp = (Long) r[3];
                rows.add(new Object[]{r[0], r[1], r[2], exp == NEVER ? NEVER : exp + shift});
            }
        }
    }

    static void randomVsReference() {
        Check.section("In-memory DB: random operation streams vs reference");
        String[][] opsByVersion = {
                {"setData", "getData", "deleteData"},
                {"setData", "getData", "deleteData", "scanData", "scanDataByPrefix"},
                {"setDataAt", "setDataAtWithTtl", "getDataAt", "deleteDataAt", "scanDataAt", "scanDataByPrefixAt"},
                {"setDataAt", "setDataAtWithTtl", "getDataAt", "deleteDataAt", "scanDataAt", "scanDataByPrefixAt",
                        "backup", "restore"},
        };
        String[] names = {"base", "FU1", "FU2", "FU3"};
        Random rnd = new Random(7);
        int cases = 800;
        for (int v = 0; v < 4; v++) {
            int mismatches = 0;
            String first = null;
            for (int it = 0; it < cases; it++) {
                Object db = newDb(v);
                Ref ref = new Ref();
                StringBuilder log = new StringBuilder();
                int t = 0, firstBackup = -1;
                for (int step = 0; step < 40; step++) {
                    t += 1 + rnd.nextInt(3);
                    String op = opsByVersion[v][rnd.nextInt(opsByVersion[v].length)];
                    if (op.equals("restore") && firstBackup < 0) op = "backup";   // a backup must exist first
                    if (op.equals("backup") && firstBackup < 0) firstBackup = t;
                    Object[] args = randomArgs(op, rnd, t, firstBackup);
                    String o = op;
                    Object exp = call(ref, o, args);
                    Check.Outcome got = Check.attempt(() -> call(db, o, args));
                    log.append("  ").append(op).append(Arrays.toString(args)).append(" -> expected ").append(Check.show(exp)).append('\n');
                    if (!got.matches(exp)) {
                        if (mismatches++ == 0) first = log + "  got: " + got;
                        if (got.timedOut) { Check.fail(names[v] + ": random operation streams", first); throw Check.abort(); }
                        break;
                    }
                }
            }
            String name = names[v] + ": random operation streams";
            if (mismatches == 0) Check.eq(name, 0, 0);
            else Check.fail(name, mismatches + " of " + cases + " random streams went wrong. First one:\n" + first);
        }
    }

    static Object newDb(int v) {
        switch (v) {
            case 0: return new inmemorydb.base.InMemoryDB();
            case 1: return new inmemorydb.fu1.InMemoryDB();
            case 2: return new inmemorydb.fu2.InMemoryDB();
            default: return new inmemorydb.fu3.InMemoryDB();
        }
    }

    static Object[] randomArgs(String op, Random rnd, int t, int firstBackup) {
        String[] keys = {"A", "B"}, fields = {"a", "ab", "b", "ba", "B"}, prefixes = {"", "a", "ab", "b", "c", "B"};
        String k = keys[rnd.nextInt(keys.length)], f = fields[rnd.nextInt(fields.length)];
        String val = "v" + rnd.nextInt(4), p = prefixes[rnd.nextInt(prefixes.length)];
        int ttl = 1 + rnd.nextInt(8);
        switch (op) {
            case "setData": return new Object[]{k, f, val};
            case "getData": case "deleteData": return new Object[]{k, f};
            case "scanData": return new Object[]{k};
            case "scanDataByPrefix": return new Object[]{k, p};
            case "setDataAt": return new Object[]{k, f, val, t};
            case "setDataAtWithTtl": return new Object[]{k, f, val, t, ttl};
            case "getDataAt": case "deleteDataAt": return new Object[]{k, f, t};
            case "scanDataAt": return new Object[]{k, t};
            case "scanDataByPrefixAt": return new Object[]{k, p, t};
            case "backup": return new Object[]{t};
            default: return new Object[]{t, firstBackup + rnd.nextInt(t - firstBackup)};   // restore
        }
    }
}
