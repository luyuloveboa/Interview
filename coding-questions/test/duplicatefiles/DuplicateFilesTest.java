package duplicatefiles;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;
import testutil.Check;

public class DuplicateFilesTest {
    public static void main(String[] args) { run(); }

    public static void run() {
        base();
        fu1();
        randomVsBruteForce();
    }

    static final String[][] EX = {{"/a/x.txt", "hello world"}, {"/c/y.txt", "hello world"}, {"/b/z.txt", "unique content"}};

    static void base() {
        Check.section("Duplicate files: base");
        b("ex", EX, "[[/a/x.txt, /c/y.txt]]");
        b("no duplicates", new String[][]{{"/a", "1"}, {"/b", "22"}, {"/c", "3"}}, "[]");
        b("single file", new String[][]{{"/only", "x"}}, "[]");
        b("all files identical", new String[][]{{"/a", "same"}, {"/b/c", "same"}, {"/d/e/f", "same"}}, "[[/a, /b/c, /d/e/f]]");
        b("two separate groups", new String[][]{{"/1", "aa"}, {"/2", "bb"}, {"/3", "aa"}, {"/4", "bb"}, {"/5", "cc"}},
                "[[/1, /3], [/2, /4]]");
        b("same size, different content", new String[][]{{"/a", "abc"}, {"/b", "abd"}}, "[]");
        b("same file name in different dirs", new String[][]{{"/x/f.txt", "1"}, {"/y/f.txt", "2"}, {"/z/f.txt", "1"}},
                "[[/x/f.txt, /z/f.txt]]");
        b("files at root and deep", new String[][]{{"/top", "q"}, {"/a/b/c/d/e/f/g/h/deep", "q"}}, "[[/a/b/c/d/e/f/g/h/deep, /top]]");
        b("directory and file side by side", new String[][]{{"/d/f", "z"}, {"/d/sub/f", "z"}, {"/d/sub/g", "y"}}, "[[/d/f, /d/sub/f]]");

        String[][] deep = new String[2][];
        StringBuilder p = new StringBuilder();
        for (int i = 0; i < 5000; i++) p.append("/d");
        deep[0] = new String[]{p + "/f", "deep"};
        deep[1] = new String[]{"/g", "deep"};
        b("5000-level deep tree (iterative walk, no stack overflow)", deep, "[[" + p + "/f, /g]]");

        FileSystem big = new FileSystem(new String[0][]);
        big.addFile("/big1", bytes(3_000_000, 7));
        big.addFile("/big2", bytes(3_000_000, 7));
        big.addFile("/big3", bytes(3_000_000, 8));
        Check.eq("3 MB files streamed", () -> norm(new duplicatefiles.base.DuplicateFileFinder(big).findDuplicateFiles()), "[[/big1, /big2]]");

        FileSystem sizes = fs(new String[][]{{"/a", "1"}, {"/b", "22"}, {"/c", "333"}, {"/d", "444"}});
        Check.eq("files with a unique size are never opened", () -> {
            new duplicatefiles.base.DuplicateFileFinder(sizes).findDuplicateFiles();
            return sizes.streamsOpened;
        }, 2);
    }

    static void fu1() {
        Check.section("Duplicate files: FU1 (read at most `limit` bytes per read)");
        f("ex (limit 5)", EX, 5, "[[/a/x.txt, /c/y.txt]]");
        f("limit 1", EX, 1, "[[/a/x.txt, /c/y.txt]]");
        f("limit bigger than every file", EX, 1000, "[[/a/x.txt, /c/y.txt]]");
        f("no duplicates", new String[][]{{"/a", "1"}, {"/b", "22"}}, 3, "[]");
        f("same first chunk, different tail", new String[][]{{"/a", "prefix-AAAA"}, {"/b", "prefix-BBBB"}, {"/c", "prefix-AAAA"}}, 4,
                "[[/a, /c]]");
        f("two groups under a small limit", new String[][]{{"/1", "xxxxyyyy"}, {"/2", "xxxxzzzz"}, {"/3", "xxxxyyyy"}, {"/4", "xxxxzzzz"}}, 3,
                "[[/1, /3], [/2, /4]]");
        f("all identical", new String[][]{{"/a", "same"}, {"/b/c", "same"}, {"/d/e/f", "same"}}, 2, "[[/a, /b/c, /d/e/f]]");

        for (int limit : new int[]{1, 5, 4096, 100_000_000}) {
            FileSystem s = fs(new String[][]{{"/a", "hello world, this is a test"}, {"/b", "hello world, this is a test"}, {"/c", "hello world, this is a tesT"}});
            Check.eq("never asks for more than limit=" + limit + " bytes in one read", () -> {
                new duplicatefiles.fu1.DuplicateFileFinder(s).findDuplicateFiles(limit);
                return s.largestReadRequest <= limit;
            }, true);
        }

        FileSystem prefix = fs(new String[][]{{"/a", "AAAA" + "x".repeat(1000)}, {"/b", "BBBB" + "x".repeat(1000)}});
        Check.eq("different first chunk: only the first chunk is read", () -> {
            new duplicatefiles.fu1.DuplicateFileFinder(prefix).findDuplicateFiles(4);
            return prefix.bytesRead;
        }, 8L);

        FileSystem small = fs(new String[][]{{"/a", "abc"}, {"/b", "abc"}, {"/c", "abc"}});
        Check.eq("files that fit in one chunk are read only once", () -> {
            new duplicatefiles.fu1.DuplicateFileFinder(small).findDuplicateFiles(10);
            return small.streamsOpened;
        }, 3);

        FileSystem big = new FileSystem(new String[0][]);
        big.addFile("/big1", bytes(3_000_000, 7));
        big.addFile("/big2", bytes(3_000_000, 7));
        big.addFile("/big3", bytes(3_000_000, 8));
        Check.eq("3 MB files, limit 1000", () -> norm(new duplicatefiles.fu1.DuplicateFileFinder(big).findDuplicateFiles(1000)) + " max read " + big.largestReadRequest,
                "[[/big1, /big2]] max read 1000");

        FileSystem sizes = fs(new String[][]{{"/a", "1"}, {"/b", "22"}, {"/c", "333"}, {"/d", "444"}});
        Check.eq("files with a unique size are never opened", () -> {
            new duplicatefiles.fu1.DuplicateFileFinder(sizes).findDuplicateFiles(2);
            return sizes.streamsOpened;
        }, 2);
    }

    static void b(String name, String[][] files, String expected) {
        Check.eq(name, () -> norm(new duplicatefiles.base.DuplicateFileFinder(fs(files)).findDuplicateFiles()), expected);
    }

    static void f(String name, String[][] files, int limit, String expected) {
        Check.eq(name, () -> norm(new duplicatefiles.fu1.DuplicateFileFinder(fs(files)).findDuplicateFiles(limit)), expected);
    }

    /** Random file systems vs grouping by the actual content string. */
    static void randomVsBruteForce() {
        Check.section("Duplicate files: random file systems vs brute force");
        Random rnd = new Random(11);
        int cases = 1000;
        int[] mismatches = new int[2];
        String[] first = new String[2];
        for (int it = 0; it < cases; it++) {
            int n = 1 + rnd.nextInt(15), limit = 1 + rnd.nextInt(6);
            String[][] files = new String[n][];
            Set<String> used = new HashSet<>();
            for (int i = 0; i < n; i++) {
                String path;
                do path = "/" + "abc".charAt(rnd.nextInt(3)) + (rnd.nextBoolean() ? "/" + "xyz".charAt(rnd.nextInt(3)) : "") + "/f" + rnd.nextInt(5);
                while (!used.add(path));
                StringBuilder content = new StringBuilder();
                int len = 1 + rnd.nextInt(8);
                for (int j = 0; j < len; j++) content.append("ab".charAt(rnd.nextInt(2)));
                files[i] = new String[]{path, content.toString()};
            }
            Map<String, List<String>> byContent = new HashMap<>();
            for (String[] fl : files) byContent.computeIfAbsent(fl[1], c -> new ArrayList<>()).add(fl[0]);
            List<List<String>> exp = new ArrayList<>();
            for (List<String> g : byContent.values()) if (g.size() >= 2) exp.add(g);
            String expected = norm(exp), desc = Arrays.deepToString(files);
            compare(0, mismatches, first, "base " + desc, () -> norm(new duplicatefiles.base.DuplicateFileFinder(fs(files)).findDuplicateFiles()), expected);
            compare(1, mismatches, first, "FU1 limit=" + limit + " " + desc,
                    () -> norm(new duplicatefiles.fu1.DuplicateFileFinder(fs(files)).findDuplicateFiles(limit)), expected);
        }
        String[] names = {"base: random file systems", "FU1: random file systems"};
        for (int v = 0; v < 2; v++) {
            if (mismatches[v] == 0) Check.eq(names[v], 0, 0);
            else Check.fail(names[v], mismatches[v] + " of " + cases + " random file systems wrong. First one:\n" + first[v]);
        }
    }

    static void compare(int v, int[] mismatches, String[] first, String call, Supplier<?> got, Object expected) {
        Check.Outcome o = Check.attempt(got);
        if (o.matches(expected)) return;
        String detail = call + "\n  expected: " + expected + "\n  got:      " + o;
        if (o.timedOut) {
            Check.fail("random file systems", detail);
            throw Check.abort();
        }
        if (mismatches[v]++ == 0) first[v] = detail;
    }

    /** Groups and paths in any order -> one canonical string. */
    static String norm(List<List<String>> groups) {
        List<List<String>> res = new ArrayList<>();
        for (List<String> g : groups) {
            List<String> s = new ArrayList<>(g);
            Collections.sort(s);
            res.add(s);
        }
        res.sort(Comparator.comparing(g -> g.get(0)));
        return res.toString();
    }

    static FileSystem fs(String[][] files) { return new FileSystem(files); }

    static byte[] bytes(int n, int seed) {
        byte[] b = new byte[n];
        new Random(seed).nextBytes(b);
        return b;
    }
}
