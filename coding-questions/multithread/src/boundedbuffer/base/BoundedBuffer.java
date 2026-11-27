package boundedbuffer.base;

/**
 * P06 — BoundedBuffer with wait/notify (Hard).
 *
 * A fixed-capacity FIFO buffer. put(item) blocks when the buffer is full
 * until there is space; take() blocks when the buffer is empty until there
 * is an item. size() returns the current element count.
 *
 * Write the blocking logic by hand with synchronized + wait() / notifyAll()
 * (or ReentrantLock + Condition). BlockingQueue is forbidden here — that is
 * P04's approach.
 *
 * Fill in the methods below, then run the tests from
 * coding-questions/multithread/:
 *   ./run-tests.sh          (macOS / Linux)
 *   .\run-tests.ps1         (Windows PowerShell)
 *
 * Note: guard every wait() with a while loop, not an if — a spurious wakeup
 * (or another thread taking the freed slot first) must send you back to
 * waiting. And use notifyAll(), not notify(): producers and consumers wait
 * on the same monitor, and notify() may wake only the wrong kind.
 */
public class BoundedBuffer<T> {

    public BoundedBuffer(int capacity) {
        // TODO: implement this constructor
        throw new UnsupportedOperationException("TODO");
    }

    public void put(T item) throws InterruptedException {
        // TODO: implement this method
        throw new UnsupportedOperationException("TODO");
    }

    public T take() throws InterruptedException {
        // TODO: implement this method
        throw new UnsupportedOperationException("TODO");
    }

    public int size() {
        // TODO: implement this method
        throw new UnsupportedOperationException("TODO");
    }
}
