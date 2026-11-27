package duplicatefiles.fu1;

import duplicatefiles.FileSystem;
import java.io.*;
import java.security.*;
import java.util.*;

public class DuplicateFileFinder {
    private final FileSystem fs;

    public DuplicateFileFinder(FileSystem fs) { this.fs = fs; }

    // [FU1 CHANGED] Three rounds of filtering, each cheaper than the next is expensive:
    // 1. group by size                      (no reads at all)
    // 2. group by hash of the FIRST chunk    (reads at most `limit` bytes per file)
    // 3. group by hash of the WHOLE file     (only for files that still collide, and only
    //                                        if they are bigger than the first chunk)
    // Every read() asks for at most `limit` bytes.
    public List<List<String>> findDuplicateFiles(int limit) {
        List<List<String>> result = new ArrayList<>();
        for (Map.Entry<Integer, List<String>> e : groupBySize().entrySet()) {
            int size = e.getKey();
            if (e.getValue().size() < 2) continue;                   // unique size: never opened
            for (List<String> samePrefix : groupBy(e.getValue(), p -> hash(p, limit, limit))) {   // [FU1 NEW]
                if (size <= limit) { result.add(samePrefix); continue; }   // [FU1 NEW] first chunk = whole file
                result.addAll(groupBy(samePrefix, p -> hash(p, size, limit)));
            }
        }
        return result;
    }

    /** [FU1 NEW] Splits paths by key; keeps only groups with 2+ paths. */
    static List<List<String>> groupBy(List<String> paths, java.util.function.Function<String, String> key) {
        Map<String, List<String>> groups = new HashMap<>();
        for (String p : paths) groups.computeIfAbsent(key.apply(p), k -> new ArrayList<>()).add(p);
        List<List<String>> res = new ArrayList<>();
        for (List<String> g : groups.values()) if (g.size() >= 2) res.add(g);
        return res;
    }

    /** Iterative DFS from "/" (no recursion, so deep trees can't overflow the stack). */
    Map<Integer, List<String>> groupBySize() {
        Map<Integer, List<String>> bySize = new HashMap<>();
        Deque<String> stack = new ArrayDeque<>(List.of("/"));
        while (!stack.isEmpty()) {
            String dir = stack.pop();
            for (String name : fs.listFiles(dir)) {
                String path = join(dir, name);
                if (fs.isDirectory(path)) stack.push(path);
                else bySize.computeIfAbsent(fs.getFileSize(path), s -> new ArrayList<>()).add(path);
            }
        }
        return bySize;
    }

    /** listFiles may return names or full paths; handle both. */
    static String join(String dir, String name) {
        if (name.startsWith("/")) return name;
        return dir.equals("/") ? "/" + name : dir + "/" + name;
    }

    /** [FU1 CHANGED] SHA-256 of the first `maxBytes` bytes, reading at most `limit` bytes per read(). */
    String hash(String path, long maxBytes, int limit) {
        try (InputStream in = fs.openStream(path)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[Math.min(limit, 1 << 20)];         // never more than limit; cap the buffer at 1 MB
            long left = maxBytes;
            for (int n; left > 0 && (n = in.read(buf, 0, (int) Math.min(buf.length, left))) > 0; left -= n)
                md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
