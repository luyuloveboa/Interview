package com.fileshare;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Background job that keeps disk and DB usage bounded by removing shares that
 * can never be downloaded again: expired ones, and ones that used up max_downloads.
 *
 * Runs every fileshare.cleanup-interval (default 10 minutes), first run one
 * interval after startup.
 */
@Component
public class ShareCleanup {

    private static final Logger logger = LoggerFactory.getLogger(ShareCleanup.class);

    /** Rows handled per query, so one run never loads the whole table into memory. */
    private static final int BATCH_SIZE = 500;

    /** A share is dead if it has expired or has no downloads left. */
    private static final String DEAD = "(expires_at <= ? OR download_count >= max_downloads)";

    private final Database database;

    public ShareCleanup(Database database) {
        this.database = database;
    }

    @Scheduled(fixedDelayString = "${fileshare.cleanup-interval}",
               initialDelayString = "${fileshare.cleanup-interval}")
    public void scheduledRun() {
        try {
            int removed = purgeDeadShares();
            if (removed > 0) {
                logger.info("Cleanup removed {} expired/exhausted shares", removed);
            }
        } catch (RuntimeException e) {
            // Never let one failed run kill the schedule; the next run retries.
            logger.error("Share cleanup failed", e);
        }
    }

    /**
     * Deletes every dead share's row and file. Returns the number of shares removed.
     *
     * For each candidate the row is deleted with the same "dead" condition
     * re-checked, and the file is removed only if that delete succeeded, so a
     * share that changed in between (or was deleted by the user) is left alone.
     */
    public int purgeDeadShares() {
        String now = FileController.nowTimestamp();
        int removed = 0;
        try (Connection conn = database.getConnection()) {
            while (true) {
                Map<String, String> batch = findDead(conn, now);
                for (Map.Entry<String, String> share : batch.entrySet()) {
                    if (deleteIfStillDead(conn, share.getKey(), now)) {
                        deleteFile(share.getValue());
                        removed++;
                    }
                }
                if (batch.size() < BATCH_SIZE) {
                    return removed;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** token -> stored_path for up to BATCH_SIZE dead shares. */
    private Map<String, String> findDead(Connection conn, String now) throws SQLException {
        Map<String, String> result = new LinkedHashMap<>();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT token, stored_path FROM shares WHERE " + DEAD + " LIMIT " + BATCH_SIZE)) {
            stmt.setString(1, now);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("token"), rs.getString("stored_path"));
                }
            }
        }
        return result;
    }

    private boolean deleteIfStillDead(Connection conn, String token, String now) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM shares WHERE token = ? AND " + DEAD)) {
            stmt.setString(1, token);
            stmt.setString(2, now);
            return stmt.executeUpdate() == 1;
        }
    }

    private void deleteFile(String storedPath) {
        try {
            Files.deleteIfExists(Paths.get(storedPath));
        } catch (IOException e) {
            // e.g. on Windows a file still being streamed to a client can't be deleted.
            logger.warn("Cleanup could not delete file {}", storedPath);
        }
    }
}
