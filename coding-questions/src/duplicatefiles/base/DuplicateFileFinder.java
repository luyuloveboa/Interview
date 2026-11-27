package duplicatefiles.base;

import duplicatefiles.FileSystem;
import java.io.*;
import java.security.*;
import java.util.*;

public class DuplicateFileFinder {
    private final FileSystem fs;

    public DuplicateFileFinder(FileSystem fs) { this.fs = fs; }

    // 1. Walk the tree and group files by size: different sizes can never be duplicates,
    //    and getFileSize is free compared with reading content.
    // 2. Only for sizes shared by 2+ files: hash the content (streamed) and group by hash.
    public List<List<String>> findDuplicateFiles() {
        List<List<String>> result = new ArrayList<>();
        for (List<String> sameSize : groupBySize().values()) {
            if (sameSize.size() < 2) continue;                       // unique size: never opened
            Map<String, List<String>> byHash = new HashMap<>();
            for (String path : sameSize) byHash.computeIfAbsent(hash(path), h -> new ArrayList<>()).add(path);
            for (List<String> group : byHash.values()) if (group.size() >= 2) result.add(group);
        }
        return result;
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

    /** SHA-256 of the whole file, read through a small fixed buffer (never the whole file at once). */
    String hash(String path) {
        try (InputStream in = fs.openStream(path)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf, 0, buf.length)) > 0; ) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
