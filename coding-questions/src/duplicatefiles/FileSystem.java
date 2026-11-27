package duplicatefiles;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Simulated file system (the interview provides this; do NOT modify its API).
 * Built from {path, content} pairs; directories are created implicitly.
 * It also records I/O statistics so tests can check the read limit and how much was read.
 */
public class FileSystem {
    private final Map<String, byte[]> files = new HashMap<>();
    private final Map<String, TreeSet<String>> children = new HashMap<>();   // dir -> child names

    // ---- I/O statistics (for tests only) ----
    public long bytesRead = 0;
    public int largestReadRequest = 0;
    public int streamsOpened = 0;

    public FileSystem(String[][] pathAndContent) {
        children.put("/", new TreeSet<>());
        for (String[] f : pathAndContent) addFile(f[0], f[1].getBytes(StandardCharsets.UTF_8));
    }

    public void addFile(String path, byte[] content) {
        files.put(path, content);
        String child = path;
        while (!child.equals("/")) {
            int slash = child.lastIndexOf('/');
            String parent = slash == 0 ? "/" : child.substring(0, slash);
            children.computeIfAbsent(parent, p -> new TreeSet<>()).add(child.substring(slash + 1));
            child = parent;
        }
    }

    /** List all entries (file and directory names, not full paths) in the directory. */
    public List<String> listFiles(String path) {
        return new ArrayList<>(children.getOrDefault(path, new TreeSet<>()));
    }

    /** Check if the path is a directory. */
    public boolean isDirectory(String path) {
        return children.containsKey(path);
    }

    /** Get the size of the file in bytes. */
    public int getFileSize(String path) {
        return files.get(path).length;
    }

    /** Open the file and return a binary stream. */
    public InputStream openStream(String path) {
        streamsOpened++;
        return new ByteArrayInputStream(files.get(path)) {
            @Override public synchronized int read() {
                largestReadRequest = Math.max(largestReadRequest, 1);
                int b = super.read();
                if (b >= 0) bytesRead++;
                return b;
            }

            @Override public synchronized int read(byte[] buf, int off, int len) {
                largestReadRequest = Math.max(largestReadRequest, len);
                int n = super.read(buf, off, len);
                if (n > 0) bytesRead += n;
                return n;
            }

            @Override public byte[] readAllBytes() {
                largestReadRequest = Math.max(largestReadRequest, count - pos);
                bytesRead += count - pos;
                return super.readAllBytes();
            }
        };
    }
}
