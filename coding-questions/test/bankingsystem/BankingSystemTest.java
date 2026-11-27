package bankingsystem;

import java.lang.reflect.Method;
import java.util.*;
import testutil.Check;

public class BankingSystemTest {
    public static void main(String[] args) { run(); }

    public static void run() {
        base();
        fu1();
        fu2();
        fu3();
        randomVsReference();
    }

    static final int DAY = 86_400_000;

    static void base() {
        Check.section("Banking system: base");
        steps("ex", new bankingsystem.base.BankingSystem(), new Object[][]{
                {"createAccount", 1, "account1", true},
                {"createAccount", 2, "account1", false},
                {"createAccount", 3, "account2", true},
                {"deposit", 4, "non-existing", 2700, -1},
                {"deposit", 5, "account1", 2700, 2700},
                {"transfer", 6, "account1", "account2", 2701, -1},
                {"transfer", 7, "account1", "account2", 200, 2500},
        });
        steps("edge", new bankingsystem.base.BankingSystem(), new Object[][]{
                {"createAccount", 1, "a", true},
                {"createAccount", 2, "b", true},
                {"deposit", 3, "a", 100, 100},
                {"deposit", 4, "a", 50, 150},
                {"transfer", 5, "a", "a", 10, -1},                  // same account, even with funds
                {"transfer", 6, "x", "a", 10, -1},                  // missing source
                {"transfer", 7, "a", "x", 10, -1},                  // missing target
                {"transfer", 8, "a", "b", 150, 0},                  // exactly the whole balance
                {"transfer", 9, "b", "a", 151, -1},
                {"deposit", 10, "b", 0, 150},                       // target received the transfer
        });
    }

    static void fu1() {
        Check.section("Banking system: FU1 (topSpenders)");
        steps("ex", new bankingsystem.fu1.BankingSystem(), new Object[][]{
                {"createAccount", 1, "account3", true},
                {"createAccount", 2, "account2", true},
                {"createAccount", 3, "account1", true},
                {"deposit", 4, "account2", 2000, 2000},
                {"deposit", 5, "account3", 3000, 3000},
                {"deposit", 6, "account1", 4000, 4000},
                {"topSpenders", 7, 3, List.of("account1(0)", "account2(0)", "account3(0)")},
                // The problem lists 3500 / 2500 / 500 for these, which contradicts its own deposits
                // (account3 starts at 3000, account1 at 4000). The correct new source balances:
                {"transfer", 8, "account3", "account2", 500, 2500},
                {"transfer", 9, "account3", "account1", 1000, 1500},
                {"transfer", 10, "account1", "account2", 2500, 2500},
                // the problem's "Output" line says account2(1500) first; its own explanation (and the math) give this:
                {"topSpenders", 11, 3, List.of("account1(2500)", "account3(1500)", "account2(0)")},
        });
        steps("edge", new bankingsystem.fu1.BankingSystem(), new Object[][]{
                {"topSpenders", 1, 3, List.of()},                   // no accounts
                {"createAccount", 2, "b", true},
                {"createAccount", 3, "a", true},
                {"createAccount", 4, "c", true},
                {"deposit", 5, "a", 100, 100},
                {"deposit", 6, "b", 100, 100},
                {"topSpenders", 7, 10, List.of("a(0)", "b(0)", "c(0)")},   // n > #accounts; deposits don't count
                {"transfer", 8, "a", "c", 30, 70},
                {"transfer", 9, "b", "c", 30, 70},
                {"transfer", 10, "b", "c", 999, -1},                // failed transfer doesn't count
                {"topSpenders", 11, 3, List.of("a(30)", "b(30)", "c(0)")},  // tie -> alphabetical
                {"topSpenders", 12, 1, List.of("a(30)")},
                {"topSpenders", 13, 0, List.of()},
        });
    }

    static void fu2() {
        Check.section("Banking system: FU2 (pay + cashback)");
        steps("ex", new bankingsystem.fu2.BankingSystem(), new Object[][]{
                {"createAccount", 1, "account1", true},
                {"createAccount", 2, "account2", true},
                {"deposit", 3, "account1", 2000, 2000},
                {"pay", 4, "account1", 1000, "payment1"},
                {"pay", 100, "account1", 1000, "payment2"},
                {"getPaymentStatus", 101, "non-existing", "payment1", ""},
                {"getPaymentStatus", 102, "account2", "payment1", ""},
                {"getPaymentStatus", 103, "account1", "payment1", "IN_PROGRESS"},
                {"topSpenders", 104, 2, List.of("account1(2000)", "account2(0)")},
                {"deposit", 86400003, "account1", 100, 100},
                {"getPaymentStatus", 86400004, "account1", "payment1", "CASHBACK_RECEIVED"},
                {"deposit", 86400005, "account1", 100, 220},
                {"deposit", 86400099, "account1", 100, 320},
                {"deposit", 86400100, "account1", 100, 440},
        });
        steps("edge", new bankingsystem.fu2.BankingSystem(), new Object[][]{
                {"pay", 1, "nobody", 10, ""},
                {"createAccount", 2, "a", true},
                {"pay", 3, "a", 10, ""},                            // insufficient
                {"deposit", 4, "a", 1000, 1000},
                {"pay", 5, "a", 49, "payment1"},                    // ids count successful payments only
                {"pay", 6, "a", 50, "payment2"},
                {"pay", 7, "a", 149, "payment3"},
                {"getPaymentStatus", 8, "a", "payment9", ""},       // unknown payment id
                {"deposit", 5 + DAY, "a", 0, 752},                  // exactly at due: 49/50 = 0 cashback applied first
                {"deposit", 7 + DAY, "a", 0, 755},                  // +1 (50) and +2 (149), rounded down
                {"getPaymentStatus", 7 + DAY, "a", "payment3", "CASHBACK_RECEIVED"},
                {"topSpenders", 8 + DAY, 1, List.of("a(248)")},     // cashback is not outgoing
        });
    }

    static void fu3() {
        Check.section("Banking system: FU3 (merge + historical balance)");
        steps("ex", new bankingsystem.fu3.BankingSystem(), new Object[][]{
                {"createAccount", 1, "account1", true},
                {"createAccount", 2, "account2", true},
                {"deposit", 3, "account1", 2000, 2000},
                {"deposit", 4, "account2", 2000, 2000},
                {"pay", 5, "account2", 300, "payment1"},
                {"transfer", 6, "account1", "account2", 500, 1500},
                {"mergeAccounts", 7, "account1", "non-existing", false},
                {"mergeAccounts", 8, "account1", "account1", false},
                {"mergeAccounts", 9, "account1", "account2", true},
                {"deposit", 10, "account1", 100, 3800},
                {"deposit", 11, "account2", 100, -1},
                {"getPaymentStatus", 12, "account2", "payment1", ""},
                {"getPaymentStatus", 13, "account1", "payment1", "IN_PROGRESS"},
                {"getBalance", 14, "account2", 1, -1},
                {"getBalance", 15, "account2", 9, -1},
                {"getBalance", 16, "account1", 11, 3800},
                {"deposit", 86400005, "account1", 100, 3906},
        });
        steps("edge", new bankingsystem.fu3.BankingSystem(), new Object[][]{
                {"createAccount", 1, "a", true},
                {"createAccount", 2, "b", true},
                {"deposit", 3, "a", 100, 100},
                {"deposit", 4, "b", 500, 500},
                {"transfer", 5, "b", "a", 200, 300},
                {"pay", 6, "b", 100, "payment1"},                   // b: 200 left, cashback 2 due at 6 + DAY
                {"mergeAccounts", 7, "a", "b", true},
                {"topSpenders", 8, 5, List.of("a(300)")},           // outgoing combined, b gone
                {"getBalance", 9, "b", 6, 200},                     // before the merge it existed
                {"getBalance", 10, "a", 3, 100},
                {"getBalance", 11, "a", 0, -1},                     // not created yet
                {"transfer", 12, "a", "b", 1, -1},                  // b no longer exists
                {"createAccount", 13, "b", true},                   // the ID can be reused
                {"getBalance", 14, "b", 12, -1},                    // gap between merge and re-creation
                {"getBalance", 15, "b", 13, 0},
                {"getPaymentStatus", 16, "b", "payment1", ""},      // payment now belongs to a
                {"deposit", 6 + DAY + 5, "a", 0, 502},              // b's pending cashback went to a
                {"getBalance", 6 + DAY + 6, "a", 6 + DAY, 502},     // recorded at its due time
                {"getBalance", 6 + DAY + 7, "a", 6 + DAY - 1, 500},
        });
    }

    /** Runs each step {method, args..., expected} as its own check. */
    static void steps(String label, Object bank, Object[][] steps) {
        for (Object[] s : steps) {
            Object[] args = Arrays.copyOfRange(s, 1, s.length - 1);
            Check.eq(label + ": " + s[0] + Arrays.toString(args), () -> call(bank, (String) s[0], args), s[s.length - 1]);
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
    // Random operation streams vs a deliberately naive reference: it rescans every payment on
    // every call and snapshots ALL balances after every step, so it shares no logic with the code.
    // ------------------------------------------------------------------------------------------

    public static class Ref {
        final Map<String, Integer> bal = new HashMap<>(), out = new HashMap<>();
        final List<Object[]> pays = new ArrayList<>();                 // {owner, cashback, due, received}
        final TreeMap<Integer, Map<String, Integer>> snaps = new TreeMap<>();

        void tick(int ts) {
            while (true) {
                Object[] best = null;
                for (Object[] p : pays)
                    if (!(Boolean) p[3] && (Integer) p[2] <= ts && (best == null || (Integer) p[2] < (Integer) best[2])) best = p;
                if (best == null) return;
                best[3] = true;
                bal.merge((String) best[0], (Integer) best[1], Integer::sum);
                snaps.put((Integer) best[2], new HashMap<>(bal));
            }
        }

        <T> T done(int ts, T result) { snaps.put(ts, new HashMap<>(bal)); return result; }

        public boolean createAccount(int ts, String id) {
            tick(ts);
            if (bal.containsKey(id)) return done(ts, false);
            bal.put(id, 0); out.put(id, 0);
            return done(ts, true);
        }

        public int deposit(int ts, String id, int amt) {
            tick(ts);
            if (!bal.containsKey(id)) return done(ts, -1);
            bal.merge(id, amt, Integer::sum);
            return done(ts, bal.get(id));
        }

        public int transfer(int ts, String s, String t, int amt) {
            tick(ts);
            if (!bal.containsKey(s) || !bal.containsKey(t) || s.equals(t) || bal.get(s) < amt) return done(ts, -1);
            bal.merge(s, -amt, Integer::sum); bal.merge(t, amt, Integer::sum); out.merge(s, amt, Integer::sum);
            return done(ts, bal.get(s));
        }

        public List<String> topSpenders(int ts, int n) {
            tick(ts);
            List<String> ids = new ArrayList<>(bal.keySet());
            ids.sort(Comparator.<String>comparingInt(out::get).reversed().thenComparing(Comparator.naturalOrder()));
            List<String> res = new ArrayList<>();
            for (int i = 0; i < Math.min(n, ids.size()); i++) res.add(ids.get(i) + "(" + out.get(ids.get(i)) + ")");
            return done(ts, res);
        }

        public String pay(int ts, String id, int amt) {
            tick(ts);
            if (!bal.containsKey(id) || bal.get(id) < amt) return done(ts, "");
            bal.merge(id, -amt, Integer::sum); out.merge(id, amt, Integer::sum);
            pays.add(new Object[]{id, (int) Math.floor(amt * 0.02), ts + DAY, false});
            return done(ts, "payment" + pays.size());
        }

        public String getPaymentStatus(int ts, String id, String pid) {
            tick(ts);
            int k = pid.startsWith("payment") ? Integer.parseInt(pid.substring(7)) : 0;
            if (!bal.containsKey(id) || k < 1 || k > pays.size() || !pays.get(k - 1)[0].equals(id)) return done(ts, "");
            return done(ts, (Boolean) pays.get(k - 1)[3] ? "CASHBACK_RECEIVED" : "IN_PROGRESS");
        }

        public boolean mergeAccounts(int ts, String a, String b) {
            tick(ts);
            if (a.equals(b) || !bal.containsKey(a) || !bal.containsKey(b)) return done(ts, false);
            bal.merge(a, bal.remove(b), Integer::sum);
            out.merge(a, out.remove(b), Integer::sum);
            for (Object[] p : pays) if (p[0].equals(b)) p[0] = a;
            return done(ts, true);
        }

        public int getBalance(int ts, String id, int timeAt) {
            tick(ts);
            Map.Entry<Integer, Map<String, Integer>> e = snaps.floorEntry(timeAt);
            return done(ts, e == null || !e.getValue().containsKey(id) ? -1 : e.getValue().get(id));
        }
    }

    static void randomVsReference() {
        Check.section("Banking system: random operation streams vs reference");
        String[][] opsByVersion = {
                {"createAccount", "deposit", "transfer"},
                {"createAccount", "deposit", "transfer", "topSpenders"},
                {"createAccount", "deposit", "transfer", "topSpenders", "pay", "getPaymentStatus"},
                {"createAccount", "deposit", "transfer", "topSpenders", "pay", "getPaymentStatus", "mergeAccounts", "getBalance"},
        };
        String[] names = {"base", "FU1", "FU2", "FU3"};
        Random rnd = new Random(21);
        int cases = 800;
        for (int v = 0; v < 4; v++) {
            int mismatches = 0;
            String first = null;
            for (int it = 0; it < cases; it++) {
                Object bank = newBank(v);
                Ref ref = new Ref();
                StringBuilder log = new StringBuilder();
                int t = 0, jumps = 0, payCount = 0;
                for (int step = 0; step < 30; step++) {
                    t += 1 + rnd.nextInt(3);
                    if (v >= 2 && jumps < 4 && rnd.nextInt(8) == 0) { t += DAY - 3 + rnd.nextInt(4); jumps++; }
                    String op = opsByVersion[v][rnd.nextInt(opsByVersion[v].length)];
                    Object[] args = randomArgs(op, rnd, t, payCount);
                    Object exp = call(ref, op, args);
                    if (op.equals("pay") && !"".equals(exp)) payCount++;
                    Object[] a = args;
                    Check.Outcome got = Check.attempt(() -> call(bank, op, a));
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

    static Object newBank(int v) {
        switch (v) {
            case 0: return new bankingsystem.base.BankingSystem();
            case 1: return new bankingsystem.fu1.BankingSystem();
            case 2: return new bankingsystem.fu2.BankingSystem();
            default: return new bankingsystem.fu3.BankingSystem();
        }
    }

    static Object[] randomArgs(String op, Random rnd, int t, int payCount) {
        String[] ids = {"a", "b", "c", "d"};
        String x = ids[rnd.nextInt(4)], y = ids[rnd.nextInt(4)];
        int amt = rnd.nextInt(120);
        switch (op) {
            case "createAccount": return new Object[]{t, x};
            case "deposit": return new Object[]{t, x, amt};
            case "transfer": return new Object[]{t, x, y, amt};
            case "topSpenders": return new Object[]{t, rnd.nextInt(5)};
            case "pay": return new Object[]{t, x, amt};
            case "getPaymentStatus": return new Object[]{t, x, "payment" + (1 + rnd.nextInt(payCount + 1))};
            case "mergeAccounts": return new Object[]{t, x, y};
            default: return new Object[]{t, x, rnd.nextInt(t + 1)};
        }
    }
}
