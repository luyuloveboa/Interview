package gpucredit;

import java.util.*;
import testutil.Check;

public class CreditSystemTest {
    public static void main(String[] args) { run(); }

    public static void run() {
        base();
        fu1();
        randomVsReference();
    }

    static void base() {
        Check.section("GPU credit: base (subtract only affects its own timestamp)");
        gpucredit.base.CreditSystem a = new gpucredit.base.CreditSystem();
        a.grantCredit("a", 3, 10, 60);
        bal("ex: after grant a", a::getBalance, 10, 3);
        a.grantCredit("b", 2, 20, 40);
        a.subtract(1, 30);
        a.subtract(3, 50);
        bal("ex", a::getBalance, 10, 3);
        bal("ex", a::getBalance, 20, 5);
        bal("ex", a::getBalance, 30, 4);
        bal("ex", a::getBalance, 35, 5);
        bal("ex", a::getBalance, 40, 3);
        bal("ex", a::getBalance, 50, 0);
        bal("expirationTime is exclusive", a::getBalance, 60, 0);
        bal("before any grant starts", a::getBalance, 9, 0);
        a.subtract(10, 15);                                        // arrives late, overdraws t=15 only
        bal("late overdraw", a::getBalance, 15, -1);
        bal("t=14 unaffected by late overdraw", a::getBalance, 14, 3);
        bal("t=50 unaffected by late overdraw", a::getBalance, 50, 0);

        gpucredit.base.CreditSystem e = new gpucredit.base.CreditSystem();
        bal("empty system", e::getBalance, 0, 0);
        e.subtract(1, 7);
        bal("subtract with no grants", e::getBalance, 7, -1);

        gpucredit.base.CreditSystem s = new gpucredit.base.CreditSystem();
        s.grantCredit("x", 5, 0, 100);
        s.subtract(2, 10);
        s.subtract(3, 10);
        bal("two subtracts at same timestamp add up", s::getBalance, 10, 0);
        s.subtract(1, 10);
        bal("three subtracts at same timestamp overdraw", s::getBalance, 10, -1);
        bal("startTime is inclusive", s::getBalance, 0, 5);
        bal("last valid moment", s::getBalance, 99, 5);
        s.grantCredit("y", 4, 10, 11);                             // grant arrives after the subtracts
        bal("late grant covers earlier subtracts", s::getBalance, 10, 3);
        bal("one-tick grant gone next tick", s::getBalance, 11, 5);
    }

    static void fu1() {
        Check.section("GPU credit: FU1 (consume soonest-expiring grant, replay in time order)");
        gpucredit.fu1.CreditSystem b = new gpucredit.fu1.CreditSystem();
        b.grantCredit("a", 3, 10, 60);
        bal("ex: after grant a", b::getBalance, 10, 3);
        b.grantCredit("b", 2, 20, 40);
        b.subtract(1, 30);
        b.subtract(3, 50);
        bal("ex", b::getBalance, 10, 3);
        bal("ex", b::getBalance, 20, 5);
        bal("ex", b::getBalance, 30, 4);
        bal("ex: usage persists after its timestamp", b::getBalance, 35, 4);
        bal("ex: b expires with 1 left unused", b::getBalance, 40, 3);
        bal("ex", b::getBalance, 50, 0);
        b.subtract(10, 15);                                        // arrives late; makes every t >= 15 invalid
        bal("before the bad usage", b::getBalance, 14, 3);
        bal("at the bad usage", b::getBalance, 15, -1);
        bal("after the bad usage", b::getBalance, 50, -1);

        gpucredit.fu1.CreditSystem c = new gpucredit.fu1.CreditSystem();
        c.subtract(2, 5);                                          // usage arrives before its grant
        bal("usage before any grant", c::getBalance, 5, -1);
        bal("time before the usage is fine", c::getBalance, 4, 0);
        c.grantCredit("late", 5, 0, 10);
        bal("grant arrives later and covers it", c::getBalance, 5, 3);
        c.grantCredit("soon", 2, 0, 6);                            // expires sooner -> should be used first
        bal("soonest-expiring used first", c::getBalance, 5, 5);
        bal("after 'soon' expires", c::getBalance, 6, 5);
        bal("after everything expires", c::getBalance, 10, 0);

        gpucredit.fu1.CreditSystem d = new gpucredit.fu1.CreditSystem();
        d.grantCredit("g1", 2, 0, 100);
        d.grantCredit("g2", 2, 0, 50);
        d.subtract(3, 10);                                         // takes 2 from g2, then 1 from g1
        bal("one usage spans two grants", d::getBalance, 10, 1);
        bal("g2 emptied, so its expiry loses nothing", d::getBalance, 50, 1);
        d.subtract(1, 60);
        bal("exactly used up", d::getBalance, 60, 0);
        d.subtract(1, 70);
        bal("one more than available", d::getBalance, 70, -1);
        bal("earlier time still ok", d::getBalance, 69, 0);

        gpucredit.fu1.CreditSystem f = new gpucredit.fu1.CreditSystem();
        f.grantCredit("old", 5, 0, 10);
        f.subtract(3, 20);                                         // "old" already expired at t=20
        bal("expired grant cannot pay", f::getBalance, 20, -1);
        f.grantCredit("future", 5, 30, 40);
        bal("grant that has not started cannot pay", f::getBalance, 20, -1);
        f.grantCredit("now", 3, 15, 25);
        bal("late grant active at usage fixes it", f::getBalance, 20, 0);
        bal("future grant once started", f::getBalance, 30, 5);
    }

    /** Random event streams compared against a unit-by-unit reference simulation. */
    static void randomVsReference() {
        Check.section("GPU credit: random event streams vs reference");
        Random rnd = new Random(7);
        int[] mismatches = new int[2];
        String[] first = new String[2];
        int cases = 1500;
        for (int it = 0; it < cases; it++) {
            List<int[]> events = new ArrayList<>();                // {0, amount, start, end} or {1, amount, t}
            int k = 1 + rnd.nextInt(8);
            for (int i = 0; i < k; i++) {
                if (rnd.nextBoolean()) {
                    int s = rnd.nextInt(20);
                    events.add(new int[]{0, 1 + rnd.nextInt(5), s, s + 1 + rnd.nextInt(15)});
                } else {
                    events.add(new int[]{1, 1 + rnd.nextInt(5), rnd.nextInt(30)});
                }
            }
            gpucredit.base.CreditSystem base = new gpucredit.base.CreditSystem();
            gpucredit.fu1.CreditSystem fu1 = new gpucredit.fu1.CreditSystem();
            for (int[] e : events) {
                if (e[0] == 0) { base.grantCredit("g", e[1], e[2], e[3]); fu1.grantCredit("g", e[1], e[2], e[3]); }
                else { base.subtract(e[1], e[2]); fu1.subtract(e[1], e[2]); }
            }
            for (int t = 0; t < 36; t++) {
                final int tt = t;
                Check.Outcome gb = Check.attempt(() -> base.getBalance(tt));
                Check.Outcome gf = Check.attempt(() -> fu1.getBalance(tt));
                int eb = refBase(events, t), ef = refFu1(events, t);
                if (!gb.matches(eb) && mismatches[0]++ == 0) first[0] = describe(events, t, gb, eb);
                if (!gf.matches(ef) && mismatches[1]++ == 0) first[1] = describe(events, t, gf, ef);
                if (gb.timedOut || gf.timedOut) {
                    if (gb.timedOut) Check.fail("base: random queries", describe(events, t, gb, eb));
                    if (gf.timedOut) Check.fail("FU1: random queries", describe(events, t, gf, ef));
                    throw Check.abort();
                }
            }
        }
        report("base", mismatches[0], first[0], cases);
        report("FU1", mismatches[1], first[1], cases);
    }

    static void report(String version, int mismatches, String first, int cases) {
        String name = version + ": random queries";
        if (mismatches == 0) Check.eq(name, 0, 0);
        else Check.fail(name, mismatches + " of " + cases * 36 + " getBalance queries wrong (" + cases
                + " random event streams x 36 timestamps). First one:\n" + first);
    }

    static String describe(List<int[]> events, int t, Check.Outcome got, int expected) {
        StringBuilder sb = new StringBuilder("events in arrival order:\n");
        for (int[] e : events)
            sb.append(e[0] == 0
                    ? "  grantCredit(\"g\", " + e[1] + ", " + e[2] + ", " + e[3] + ")\n"
                    : "  subtract(" + e[1] + ", " + e[2] + ")\n");
        return sb.append("getBalance(").append(t).append("): expected ").append(expected).append(", got ").append(got).toString();
    }

    static int refBase(List<int[]> events, int t) {
        int total = 0;
        for (int[] e : events) {
            if (e[0] == 0 && e[2] <= t && t < e[3]) total += e[1];
            if (e[0] == 1 && e[2] == t) total -= e[1];
        }
        return total < 0 ? -1 : total;
    }

    /** Walks time forward one tick at a time, paying each usage one credit at a time. */
    static int refFu1(List<int[]> events, int t) {
        List<int[]> grants = new ArrayList<>();                    // {left, start, end}
        for (int[] e : events) if (e[0] == 0) grants.add(new int[]{e[1], e[2], e[3]});
        for (int now = 0; now <= t; now++) {
            int need = 0;
            for (int[] e : events) if (e[0] == 1 && e[2] == now) need += e[1];
            for (; need > 0; need--) {
                int[] best = null;
                for (int[] g : grants)
                    if (g[0] > 0 && g[1] <= now && now < g[2] && (best == null || g[2] < best[2])) best = g;
                if (best == null) return -1;
                best[0]--;
            }
        }
        int total = 0;
        for (int[] g : grants) if (g[1] <= t && t < g[2]) total += g[0];
        return total;
    }

    interface Query { int at(int t); }

    static void bal(String name, Query q, int t, int expected) {
        Check.eq(name + ": getBalance(" + t + ")", () -> q.at(t), expected);
    }
}
