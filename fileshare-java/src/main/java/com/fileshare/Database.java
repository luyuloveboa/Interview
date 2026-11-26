package com.fileshare;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Owns the SQLite database: a HikariCP connection pool plus the table schema.
 *
 * Callers borrow a connection with getConnection() and must close it
 * (try-with-resources); closing returns it to the pool instead of closing the file.
 */
@Component
public class Database {

    private final HikariDataSource dataSource;

    /**
     * @param dbPath   path to the SQLite file (fileshare.db-path, env DB_PATH)
     * @param poolSize max pooled connections (fileshare.db-pool-size, env DB_POOL_SIZE)
     */
    public Database(@Value("${fileshare.db-path}") String dbPath,
                    @Value("${fileshare.db-pool-size:10}") int poolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbPath);
        config.setMaximumPoolSize(poolSize);
        config.setPoolName("fileshare-sqlite");
        // How long a connection waits for another writer's lock before failing with
        // SQLITE_BUSY (sqlite-jdbc defaults to 3000ms; set explicitly so it's tunable).
        config.addDataSourceProperty("busy_timeout", "5000");
        this.dataSource = new HikariDataSource(config);
    }

    /** Borrows a pooled connection. Caller must close it to return it to the pool. */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /** Closes all pooled connections when the app shuts down. */
    @PreDestroy
    public void close() {
        dataSource.close();
    }

    /**
     * Creates / migrates the schema. Called once at startup from
     * FileshareApplication.initDatabase().
     *
     * One row in "shares" = one uploaded file / share link:
     *   token             - random 128-bit id in the share URL (<base-url>/<token>), primary key
     *   filename          - sanitized display name (client filename minus path/control chars)
     *   stored_path       - where the bytes were written on disk: <uploadDir>/<token>
     *   content_type      - MIME type sent by the client
     *   size_bytes        - file size on disk
     *   uploaded_at       - set by SQLite (UTC, format "YYYY-MM-DD HH:MM:SS")
     *   expires_at        - set by the app (UTC, format "YYYY-MM-DDTHH:MM:SS", e.g. "2026-11-20T10:00:00")
     *   max_downloads     - how many downloads are allowed
     *   download_count    - how many downloads have happened so far
     *   delete_token_hash - SHA-256 (hex) of the uploader's delete token; NULL for
     *                       shares created before delete tokens existed (cannot be deleted via API)
     */
    public void init() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // WAL lets readers proceed while a write is in progress. The setting is
            // stored in the DB file, so running it once at startup is enough.
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS shares (
                    token TEXT PRIMARY KEY,
                    filename TEXT NOT NULL,
                    stored_path TEXT NOT NULL,
                    content_type TEXT,
                    size_bytes INTEGER,
                    uploaded_at TEXT DEFAULT CURRENT_TIMESTAMP,
                    expires_at TEXT,
                    max_downloads INTEGER,
                    download_count INTEGER DEFAULT 0,
                    delete_token_hash TEXT
                )
            """);
            // Migration for databases created before delete tokens were added.
            if (!hasColumn(stmt, "shares", "delete_token_hash")) {
                stmt.execute("ALTER TABLE shares ADD COLUMN delete_token_hash TEXT");
            }
            // Lets the cleanup job find expired shares without a full table scan.
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_shares_expires_at ON shares(expires_at)");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean hasColumn(Statement stmt, String table, String column) throws SQLException {
        try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }
}
