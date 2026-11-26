import java.util.HashMap;
import java.util.Map;

/**
 * A tiny fixed-capacity LRU cache (String keys and values).
 *
 * <p>API: {@link #get(String)}, {@link #put(String, String)}, {@link #size()}.
 * Data model: a HashMap from key to node, plus a hand-rolled doubly-linked
 * list ordered most-recently-used first. The node at the head is the most
 * recently used; the node at the tail is evicted when the cache is full.
 *
 * <p>AC2 practice: this starter code contains ONE bug. Do not read the
 * test file first to find it. Follow the R6 workflow: reproduce it first
 * (input / expected / actual + your own hypothesis), then ask the AI,
 * then run the full regression suite after the fix.
 */
public class SimpleCache {

    private static final class Node {
        final String key;
        String value;
        Node prev;
        Node next;

        Node(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    private final int capacity;
    private final Map<String, Node> index = new HashMap<>();
    // Dummy head and tail: head.next is most-recently-used, tail.prev is least.
    private final Node head = new Node(null, null);
    private final Node tail = new Node(null, null);

    public SimpleCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        head.next = tail;
        tail.prev = head;
    }

    /** Returns the value, or null if the key is missing. A hit refreshes recency. */
    public String get(String key) {
        Node node = index.get(key);
        if (node == null) {
            return null;
        }
        moveToHead(node);
        return node.value;
    }

    /** Stores the value under key, evicting the least-recently-used entry if full. */
    public void put(String key, String value) {
        Node existing = index.get(key);
        if (existing != null) {
            existing.value = value;
            return;
        }
        Node node = new Node(key, value);
        index.put(key, node);
        addToHead(node);
        if (index.size() > capacity) {
            Node leastUsed = tail.prev;
            removeNode(leastUsed);
            index.remove(leastUsed.key);
        }
    }

    public int size() {
        return index.size();
    }

    private void addToHead(Node node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }

    private void removeNode(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void moveToHead(Node node) {
        removeNode(node);
        addToHead(node);
    }
}
