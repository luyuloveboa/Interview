package profilerevents;

import java.util.*;
import testutil.Check;

public class ProfilerEventsTest {
    public static void main(String[] args) { run(); }

    public static void run() {
        base();
        fu1();
        fu2();
        randomVsReference();
    }

    static void base() {
        Check.section("Profiler events: base");
        base("ex1", L("10:main->calc", "20:main->calc->helper", "30:main->calc->helper->helper", "40:main->calc->helper", "50:main"),
                L("start:10:main", "start:10:calc", "start:20:helper", "start:30:helper", "end:40:helper", "end:50:helper", "end:50:calc"));
        base("ex2", L("5:main", "10:main->foo", "15:main->foo->bar", "20:main"),
                L("start:5:main", "start:10:foo", "start:15:bar", "end:20:bar", "end:20:foo"));
        base("ex3", L("0:", "10:main", "20:main->process"),
                L("start:10:main", "start:20:process"));
        base("single sample", L("1:a"), L("start:1:a"));
        base("only empty stacks", L("1:", "2:"), L());
        base("stack empties: ends inner -> outer", L("1:a->b", "2:"),
                L("start:1:a", "start:1:b", "end:2:b", "end:2:a"));
        base("root replaced: same names below are new frames", L("1:a->b", "2:c->b"),
                L("start:1:a", "start:1:b", "end:2:b", "end:2:a", "start:2:c", "start:2:b"));
        base("recursion: each a is its own frame", L("1:a->b->a", "2:a->b->a->c", "3:a"),
                L("start:1:a", "start:1:b", "start:1:a", "start:2:c", "end:3:c", "end:3:a", "end:3:b"));
        base("same stack repeated", L("1:a->b", "2:a->b", "3:a->b"), L("start:1:a", "start:1:b"));
        base("middle frame changes: everything below it restarts", L("1:a->b->c", "2:a->x->c"),
                L("start:1:a", "start:1:b", "start:1:c", "end:2:c", "end:2:b", "start:2:x", "start:2:c"));
        base("disappears then comes back", L("1:a", "2:", "3:a"), L("start:1:a", "end:2:a", "start:3:a"));
        base("digits and underscores in names", L("7:main_1->f2"), L("start:7:main_1", "start:7:f2"));
    }

    static void fu1() {
        Check.section("Profiler events: FU1 (prefix paths, n consecutive)");
        fu1("ex1", L("1:main", "2:main->A", "3:main->A", "4:main->B", "5:main->B->C", "6:main->B"), 2,
                L("start:1:main", "start:2:A", "end:4:A", "start:4:B"));
        fu1("ex2", L("10:main", "20:main->A", "30:main"), 2, L("start:10:main"));
        fu1("ex3", L("1:main", "2:main->A", "3:main->A", "4:main->A", "5:main"), 3,
                L("start:1:main", "start:2:A", "end:5:A"));
        fu1("n = 1 is the base version", L("10:main->calc", "20:main->calc->helper", "30:main->calc->helper->helper", "40:main->calc->helper", "50:main"), 1,
                L("start:10:main", "start:10:calc", "start:20:helper", "start:30:helper", "end:40:helper", "end:50:helper", "end:50:calc"));
        fu1("n larger than the number of samples", L("1:a", "2:a"), 3, L());
        fu1("streak breaks, then a new streak starts", L("1:a", "2:a", "3:", "4:a", "5:a"), 2,
                L("start:1:a", "end:3:a", "start:4:a"));
        fu1("interrupted streaks never count", L("1:a", "2:", "3:a", "4:"), 2, L());
        fu1("child streak restarts while the parent continues", L("1:m", "2:m->x", "3:m->x", "4:m->y", "5:m->x"), 2,
                L("start:1:m", "start:2:x", "end:4:x"));
        fu1("new root: children are new paths", L("1:a->b", "2:c->b", "3:c->b"), 2,
                L("start:2:c", "start:2:b"));
        fu1("recursion", L("1:a->a", "2:a->a", "3:a"), 2, L("start:1:a", "start:1:a", "end:3:a"));
        fu1("end and start in the same sample", L("1:m->a", "2:m->a", "3:m->b", "4:m->b"), 2,
                L("start:1:m", "start:1:a", "end:3:a", "start:3:b"));
        fu1("everything ends at once, inner -> outer", L("1:a->b->c", "2:a->b->c", "3:"), 2,
                L("start:1:a", "start:1:b", "start:1:c", "end:3:c", "end:3:b", "end:3:a"));
    }

    static void fu2() {
        Check.section("Profiler events: FU2 (suffix paths, n consecutive)");
        fu2("ex1", L("10:taskA->process->save", "20:taskB->process->save", "30:taskB->process->save", "40:taskB->flush"), 2,
                L("start:10:process", "start:10:save", "start:20:taskB", "end:40:save", "end:40:process", "end:40:taskB"));
        fu2("ex2", L("10:task1->work->leaf", "20:task2->work->leaf", "30:task2->work->leaf"), 2,
                L("start:10:work", "start:10:leaf", "start:20:task2"));
        fu2("ex3", L("10:main->process->save", "20:main->process->save", "30:main->process->flush", "40:main->process->save", "50:main->process->save"), 2,
                L("start:10:main", "start:10:process", "start:10:save", "end:30:save", "end:30:process", "end:30:main",
                        "start:40:main", "start:40:process", "start:40:save"));
        fu2("n = 1: a new root only replaces the root", L("1:a->b", "2:c->b"), 1,
                L("start:1:a", "start:1:b", "end:2:a", "start:2:c"));
        fu2("changing roots don't break the leaf's streak", L("1:x->w->l", "2:y->w->l", "3:z->w->l"), 3,
                L("start:1:w", "start:1:l"));
        fu2("new leaf breaks every suffix", L("1:m->a", "2:m->a", "3:m->b"), 2,
                L("start:1:m", "start:1:a", "end:3:a", "end:3:m"));
        fu2("leaf returns: the caller is a different suffix now", L("1:m->a", "2:m->a", "3:m"), 2,
                L("start:1:m", "start:1:a", "end:3:a", "end:3:m"));
        fu2("leaf pushed: the old frame restarts under a new identity", L("1:m", "2:m", "3:m->a", "4:m->a"), 2,
                L("start:1:m", "end:3:m", "start:3:m", "start:3:a"));
        fu2("empty stacks", L("1:", "2:a", "3:a", "4:"), 2, L("start:2:a", "end:4:a"));
        fu2("n larger than the number of samples", L("1:a", "2:a"), 3, L());
        fu2("recursion", L("1:a->a", "2:b->a->a", "3:b->a->a"), 2,
                L("start:1:a", "start:1:a", "start:2:b"));
        fu2("only the outer part ends", L("1:x->m->a", "2:x->m->a", "3:y->m->a"), 2,
                L("start:1:x", "start:1:m", "start:1:a", "end:3:x"));
    }

    static List<String> L(String... s) { return List.of(s); }

    static void base(String name, List<String> samples, List<String> expected) {
        Check.eq(name, () -> new profilerevents.base.Solution().generateEvents(samples), expected);
    }

    static void fu1(String name, List<String> samples, int n, List<String> expected) {
        Check.eq(name, () -> new profilerevents.fu1.Solution().generateEvents(samples, n), expected);
    }

    static void fu2(String name, List<String> samples, int n, List<String> expected) {
        Check.eq(name, () -> new profilerevents.fu2.Solution().generateEvents(samples, n), expected);
    }

    // ------------------------------------------------------------------------------------------
    // Brute force: every path is an explicit string key ("depth|a->b->c"), streaks live in a
    // HashMap, and events come from set differences between the old and new valid lists.
    // None of the common-prefix / index tricks from the solutions.
    // ------------------------------------------------------------------------------------------

    static List<String> brute(List<String> samples, int n, boolean suffix) {
        List<String> out = new ArrayList<>();
        Map<String, int[]> prevState = new HashMap<>();             // path -> {streak, start}
        List<String> prevValid = new ArrayList<>();                 // outer -> inner
        Map<String, String> fnOf = new HashMap<>();
        for (String s : samples) {
            int time = Integer.parseInt(s.substring(0, s.indexOf(':')));
            String body = s.substring(s.indexOf(':') + 1);
            List<String> st = body.isEmpty() ? List.of() : Arrays.asList(body.split("->"));
            List<String> paths = new ArrayList<>();                 // outer -> inner
            for (int i = 0; i < st.size(); i++) {
                List<String> part = suffix ? st.subList(i, st.size()) : st.subList(0, i + 1);
                String key = part.size() + "|" + String.join("->", part);
                fnOf.put(key, st.get(i));
                paths.add(key);
            }
            Map<String, int[]> state = new HashMap<>();
            List<String> valid = new ArrayList<>();
            for (String p : paths) {
                int[] old = prevState.get(p);
                int[] now = old == null ? new int[]{1, time} : new int[]{old[0] + 1, old[1]};
                state.put(p, now);
                if (now[0] >= n) valid.add(p);
            }
            Set<String> validSet = new HashSet<>(valid), prevSet = new HashSet<>(prevValid);
            for (int i = prevValid.size() - 1; i >= 0; i--)
                if (!validSet.contains(prevValid.get(i))) out.add("end:" + time + ":" + fnOf.get(prevValid.get(i)));
            for (String p : valid)
                if (!prevSet.contains(p)) out.add("start:" + state.get(p)[1] + ":" + fnOf.get(p));
            prevState = state;
            prevValid = valid;
        }
        return out;
    }

    static void randomVsReference() {
        Check.section("Profiler events: random samples vs brute force");
        Random rnd = new Random(11);
        String[] names = {"a", "b", "c"};                           // tiny alphabet: lots of recursion and collisions
        int cases = 3000;
        int[] mismatches = new int[3];
        String[] first = new String[3];
        for (int it = 0; it < cases; it++) {
            int n = 1 + rnd.nextInt(3);
            List<String> samples = new ArrayList<>();
            List<String> stack = new ArrayList<>();
            for (int i = 0, m = 1 + rnd.nextInt(10); i < m; i++) {
                if (rnd.nextInt(6) == 0) stack.clear();
                for (int p = rnd.nextInt(stack.size() + 1) / (1 + rnd.nextInt(2)); p > 0; p--) stack.remove(stack.size() - 1);
                for (int p = rnd.nextInt(3); p > 0 && stack.size() < 5; p--) stack.add(names[rnd.nextInt(names.length)]);
                if (!stack.isEmpty() && rnd.nextInt(4) == 0) stack.set(0, names[rnd.nextInt(names.length)]);   // new root
                samples.add(i * 10 + ":" + String.join("->", stack));
            }
            Object[][] runs = {
                    {Check.attempt(() -> new profilerevents.base.Solution().generateEvents(samples)), brute(samples, 1, false), "n=1"},
                    {Check.attempt(() -> new profilerevents.fu1.Solution().generateEvents(samples, n)), brute(samples, n, false), "n=" + n},
                    {Check.attempt(() -> new profilerevents.fu2.Solution().generateEvents(samples, n)), brute(samples, n, true), "n=" + n},
            };
            for (int v = 0; v < 3; v++) {
                Check.Outcome got = (Check.Outcome) runs[v][0];
                if (!got.matches(runs[v][1]) && mismatches[v]++ == 0)
                    first[v] = "samples = " + samples + ", " + runs[v][2] + "\nexpected: " + runs[v][1] + "\ngot:      " + got;
            }
        }
        String[] label = {"base", "FU1", "FU2"};
        for (int v = 0; v < 3; v++) {
            String name = label[v] + ": random samples";
            if (mismatches[v] == 0) Check.eq(name, 0, 0);
            else Check.fail(name, mismatches[v] + " of " + cases + " random cases went wrong. First one:\n" + first[v]);
        }
    }
}
