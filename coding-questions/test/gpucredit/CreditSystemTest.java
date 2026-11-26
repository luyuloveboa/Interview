package gpucredit;

import java.util.*;
import testutil.Check;

public class CreditSystemTest {
    public static void main(String[] args) { run(); }

    public static void run() {
        Check.section("GPU credit: base (subtract only affects its own timestamp)");
        gpucredit.base.CreditSystem a = new gpucredit.base.CreditSystem();
        a.grantCredit("a", 3, 10, 60);
        Check.eq("getBalance(10) first", a.getBalance(10), 3);
        a.grantCredit("b", 2, 20, 40);
        a.subtract(1, 30);
        a.subtract(3, 50);
        Check.eq("example", balances(a::getBalance, 10, 20, 30, 35, 40, 50), new int[]{3, 5, 4, 5, 3, 0});
        Check.eq("expirationTime is exclusive", a.getBalance(60), 0);
        a.subtract(10, 15);                                        // arrives late, overdraws t=15 only
        Check.eq("late overdraw at 15", a.getBalance(15), -1);
        Check.eq("t=14 unaffected", a.getBalance(14), 3);
        Check.eq("t=50 unaffected", a.getBalance(50), 0);

        Check.section("GPU credit: FU1 (consume soonest-expiring grant, replay in time order)");
        gpucredit.fu1.CreditSystem b = new gpucredit.fu1.CreditSystem();
        b.grantCredit("a", 3, 10, 60);
        Check.eq("getBalance(10) first", b.getBalance(10), 3);
        b.grantCredit("b", 2, 20, 40);
        b.subtract(1, 30);
        b.subtract(3, 50);
        Check.eq("example", balances(b::getBalance, 10, 20, 30, 35, 40, 50), new int[]{3, 5, 4, 4, 3, 0});
        b.subtract(10, 15);                                        // arrives late; makes every t >= 15 invalid
        Check.eq("before the bad usage", b.getBalance(14), 3);
        Check.eq("at the bad usage", b.getBalance(15), -1);
        Check.eq("after the bad usage", b.getBalance(50), -1);

        gpucredit.fu1.CreditSystem c = new gpucredit.fu1.CreditSystem();
        c.subtract(2, 5);                                          // usage arrives before its grant
        Check.eq("usage before any grant", c.getBalance(5), -1);
        c.grantCredit("late", 5, 0, 10);
        Check.eq("grant arrives later and covers it", c.getBalance(5), 3);
        c.grantCredit("soon", 2, 0, 6);                            // expires sooner -> should be used first
        Check.eq("soonest-expiring used first", c.getBalance(5), 5);
        Check.eq("after 'soon' expires", c.getBalance(6), 5);
    }

    interface Query { int at(int t); }

    static int[] balances(Query q, int... ts) {
        int[] res = new int[ts.length];
        for (int i = 0; i < ts.length; i++) res[i] = q.at(ts[i]);
        return res;
    }
}
