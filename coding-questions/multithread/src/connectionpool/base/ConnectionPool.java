package connectionpool.base;

/**
 * P07 — Connection Pool with Semaphore (Medium).
 *
 * A pool of N database connections, with ids "conn-0" ... "conn-(N-1)".
 * acquire() blocks when all connections are in use and returns a free
 * connection's id; release(id) returns it to the pool.
 *
 * Releasing a connection that is NOT currently checked out (a double release,
 * or an id that was never borrowed) MUST be a no-op and must NOT create an
 * extra permit — a double release must never let the pool lend out more than
 * N connections at once. A bare Semaphore alone is NOT enough: it counts
 * returns, not connections.
 *
 * Fill in the constructor, acquire() and release() below, then run the tests
 * from coding-questions/multithread/:
 *   ./run-tests.sh          (macOS / Linux)
 *   .\run-tests.ps1         (Windows PowerShell)
 */
public class ConnectionPool {

    public ConnectionPool(int size) {
        // TODO: implement this constructor
        throw new UnsupportedOperationException("TODO");
    }

    public String acquire() throws InterruptedException {
        // TODO: implement this method
        throw new UnsupportedOperationException("TODO");
    }

    public void release(String connectionId) {
        // TODO: implement this method
        throw new UnsupportedOperationException("TODO");
    }
}
