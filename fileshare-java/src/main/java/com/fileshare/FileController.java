package com.fileshare;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * All HTTP endpoints of the service live here:
 *
 *   POST   /upload         -> store a file, create a share, return token + share_url + delete_token
 *   GET    /{token}        -> download the file (counts toward max_downloads)
 *   GET    /info/{token}   -> metadata about a share (does NOT count as a download)
 *   DELETE /{token}        -> delete the share; requires header X-Delete-Token
 *
 * Two secrets are issued per share:
 *   token        - in the share link; lets anyone who has the link download and view /info
 *   delete_token - returned only to the uploader; required to DELETE. Only its
 *                  SHA-256 hash is stored, so a DB leak doesn't leak delete rights.
 * /info stays public on purpose: it exposes nothing the downloader can't already
 * see (name, size, type, expiry, counts).
 *
 * Lifecycle of a share:
 *   upload -> row inserted with download_count = 0, bytes saved as <uploadDir>/<token>
 *   each download -> download_count + 1 (atomically); refused with 410 once it
 *                    reaches max_downloads or once expires_at has passed
 *   delete (by uploader) or ShareCleanup (once dead) -> row + file removed
 *
 * Errors are returned as JSON like {"error": "..."}; unexpected SQL/IO errors
 * are wrapped in RuntimeException and become HTTP 500.
 */
@RestController
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    /** Header carrying the delete token on DELETE requests. */
    static final String DELETE_TOKEN_HEADER = "X-Delete-Token";

    /** Tokens carry 128 bits of randomness (22 URL-safe base64 chars). */
    private static final int TOKEN_BYTES = 16;

    /** Fixed-width UTC timestamp format for expires_at, e.g. "2026-11-20T10:00:00". */
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final Database database;
    /** Directory where uploaded file bytes are written (fileshare.upload-dir). */
    private final Path uploadDir;
    /** Public origin for share links, without trailing slash (fileshare.base-url). */
    private final String baseUrl;
    /** Largest accepted file, in bytes (spring.servlet.multipart.max-file-size). */
    private final long maxUploadBytes;
    /** Cryptographically secure RNG so tokens cannot be predicted. */
    private final SecureRandom random = new SecureRandom();

    /**
     * Spring injects the Database bean, the upload dir, the public base URL and the
     * multipart limits; the upload directory is created if missing.
     */
    public FileController(Database database,
                          @Value("${fileshare.upload-dir}") String uploadDir,
                          @Value("${fileshare.base-url}") String baseUrl,
                          MultipartProperties multipartProperties) {
        this.database = database;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.maxUploadBytes = multipartProperties.getMaxFileSize().toBytes();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Builds a random, unguessable token, e.g. "q2Zs8_Xk1v9mR0aT-4bLcw".
     * The alphabet (A-Z a-z 0-9 - _) is safe both in URLs and as a filename on disk.
     * Used for both share tokens and delete tokens.
     */
    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 hex of a token. Tokens are 128-bit random, so a fast unsalted hash
     * is enough (no dictionary to brute-force, unlike passwords).
     */
    static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Current UTC time in the same fixed-width format as expires_at (comparable as strings). */
    static String nowTimestamp() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).format(TIMESTAMP);
    }

    /**
     * Turns the client-supplied filename into a safe display name:
     * drops any directory part ("../../x" -> "x", "C:\a\b" -> "b"), removes control
     * characters (CR/LF etc.), and falls back to "file" if nothing is left.
     * The display name is only stored as metadata; it is never used as a path.
     */
    static String sanitizeFilename(String original) {
        if (original == null) {
            return "file";
        }
        String name = original;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            return "file";
        }
        return name;
    }

    private static ResponseEntity<Object> error(int status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }

    /**
     * POST /upload  (multipart form)
     *   file             - required, the file to share, at most max-file-size bytes
     *   expires_in_hours - optional, default 24, must be >= 1
     *   max_downloads    - optional, default 10, must be >= 1
     *
     * Steps:
     *   1. Validate input; 400 on a missing file or non-positive limits, 413 if too large.
     *      (In a real server Spring's multipart parser rejects oversized uploads before
     *      this method runs, see ApiExceptionHandler; the size check here is a backstop.)
     *   2. Generate the share token and the delete token.
     *   3. Write the bytes to <uploadDir>/<token> (never to a client-chosen path).
     *   4. Insert a row into "shares" (storing only the delete token's hash);
     *      if that fails, remove the orphaned file.
     *   5. Return 201 with token, share_url, delete_token, expires_at, max_downloads.
     *      The delete_token is shown only here, once.
     */
    @PostMapping("/upload")
    public ResponseEntity<Object> upload(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "expires_in_hours", defaultValue = "24") int expiresInHours,
            @RequestParam(value = "max_downloads", defaultValue = "10") int maxDownloads) {
        // 1. Validate input.
        if (file == null) {
            return error(400, "file required");
        }
        if (file.getSize() > maxUploadBytes) {
            return error(413, "file too large");
        }
        if (expiresInHours < 1) {
            return error(400, "expires_in_hours must be at least 1");
        }
        if (maxDownloads < 1) {
            return error(400, "max_downloads must be at least 1");
        }

        String filename = sanitizeFilename(file.getOriginalFilename());
        logger.info("Uploading {}", filename);

        // 2. Token in the share link + separate secret that authorizes deletion.
        String token = generateToken();
        String deleteToken = generateToken();

        // 3. Save the bytes under the token, so names can't escape the dir or collide.
        Path storedPath = uploadDir.resolve(token);
        long size;
        try {
            file.transferTo(storedPath);
            size = Files.size(storedPath);
        } catch (IOException e) {
            deleteQuietly(storedPath);
            throw new RuntimeException(e);
        }

        // Expiry = now (UTC) + N hours, stored as a fixed-width "yyyy-MM-ddTHH:mm:ss" string.
        String expiresAt = LocalDateTime.now(ZoneOffset.UTC).plusHours(expiresInHours)
                .truncatedTo(ChronoUnit.SECONDS).format(TIMESTAMP);

        // 4. Record the share's metadata (download_count starts at 0 via the column default).
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                INSERT INTO shares (token, filename, stored_path, content_type, size_bytes,
                                    expires_at, max_downloads, delete_token_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """)) {
            stmt.setString(1, token);
            stmt.setString(2, filename);
            stmt.setString(3, storedPath.toString());
            stmt.setString(4, file.getContentType());
            stmt.setLong(5, size);
            stmt.setString(6, expiresAt);
            stmt.setInt(7, maxDownloads);
            stmt.setString(8, sha256Hex(deleteToken));
            stmt.executeUpdate();
        } catch (SQLException e) {
            deleteQuietly(storedPath);
            throw new RuntimeException(e);
        }

        // 5. Response: the public link, plus the uploader-only delete secret.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        body.put("share_url", baseUrl + "/" + token);
        body.put("delete_token", deleteToken);
        body.put("expires_at", expiresAt);
        body.put("max_downloads", maxDownloads);
        return ResponseEntity.status(201).body(body);
    }

    /**
     * GET /{token}  -> streams the file back as an attachment.
     *
     * Steps:
     *   1. Look up the share; 404 if the token is unknown.
     *   2. 410 Gone if the share has expired.
     *   3. 404 if the file is missing on disk (checked before using up a download).
     *   4. Atomically increment download_count only while it is below max_downloads;
     *      if no row was updated the limit is used up -> 410. A single conditional
     *      UPDATE means concurrent requests can never exceed the limit.
     *   5. Return the file with a safely encoded Content-Disposition header.
     */
    @GetMapping("/{token}")
    public ResponseEntity<Object> download(@PathVariable String token) {
        try (Connection conn = database.getConnection()) {
            // 1. Find the share.
            String filename;
            String storedPath;
            String contentType;
            String expiresAt;
            try (PreparedStatement select = conn.prepareStatement(
                    "SELECT filename, stored_path, content_type, expires_at FROM shares WHERE token = ?")) {
                select.setString(1, token);
                try (ResultSet share = select.executeQuery()) {
                    if (!share.next()) {
                        return error(404, "not found");
                    }
                    filename = share.getString("filename");
                    storedPath = share.getString("stored_path");
                    contentType = share.getString("content_type");
                    expiresAt = share.getString("expires_at");
                }
            }

            // 2. Enforce expiry.
            if (isExpired(expiresAt)) {
                return error(410, "share expired");
            }

            // 3. Don't burn a download on a file that no longer exists.
            Path path = Paths.get(storedPath);
            if (!Files.isRegularFile(path)) {
                logger.warn("File for share {} is missing on disk", token);
                return error(404, "file not found");
            }

            // 4. Count this download, atomically enforcing the limit.
            try (PreparedStatement update = conn.prepareStatement("""
                    UPDATE shares SET download_count = download_count + 1
                    WHERE token = ? AND download_count < max_downloads
                """)) {
                update.setString(1, token);
                if (update.executeUpdate() == 0) {
                    return error(410, "download limit reached");
                }
            }

            // 5. Send the file; the browser saves it under the original (sanitized) filename.
            Resource resource = new FileSystemResource(path);
            ContentDisposition disposition = ContentDisposition.attachment()
                    .filename(filename, StandardCharsets.UTF_8)
                    .build();
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(parseContentType(contentType))
                .body(resource);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * GET /info/{token}  -> JSON metadata for a share (filename, size, expiry,
     * download counts, ...). Read-only: does not change download_count.
     * Never returns the delete token or its hash. Returns 404 if the token is unknown.
     */
    @GetMapping("/info/{token}")
    public ResponseEntity<Object> info(@PathVariable String token) {
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                SELECT token, filename, content_type, size_bytes, uploaded_at,
                       expires_at, max_downloads, download_count
                FROM shares WHERE token = ?
            """)) {
            stmt.setString(1, token);
            try (ResultSet share = stmt.executeQuery()) {
                if (!share.next()) {
                    return error(404, "not found");
                }

                // Copy the row into an ordered map, which Spring serializes as JSON.
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("token", share.getString("token"));
                body.put("filename", share.getString("filename"));
                body.put("content_type", share.getString("content_type"));
                body.put("size_bytes", share.getObject("size_bytes"));
                body.put("uploaded_at", share.getString("uploaded_at"));
                body.put("expires_at", share.getString("expires_at"));
                body.put("max_downloads", share.getObject("max_downloads"));
                body.put("download_count", share.getObject("download_count"));
                return ResponseEntity.ok(body);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * DELETE /{token}  with header X-Delete-Token: <delete_token from upload>
     *
     * Steps:
     *   1. Look up the share; 404 if the token is unknown.
     *   2. 401 if the X-Delete-Token header is missing; 403 if it doesn't match
     *      (compared by hash in constant time). Shares created before delete tokens
     *      existed have no hash and always get 403.
     *   3. Delete the row first, so the share stops being downloadable immediately.
     *   4. Delete the file from disk (a failure is only logged; the file is unreachable anyway).
     */
    @DeleteMapping("/{token}")
    public ResponseEntity<Object> delete(
            @PathVariable String token,
            @RequestHeader(value = DELETE_TOKEN_HEADER, required = false) String deleteToken) {
        try (Connection conn = database.getConnection()) {
            // 1. Find the share and its delete-token hash.
            String storedPath;
            String expectedHash;
            try (PreparedStatement select = conn.prepareStatement(
                    "SELECT stored_path, delete_token_hash FROM shares WHERE token = ?")) {
                select.setString(1, token);
                try (ResultSet share = select.executeQuery()) {
                    if (!share.next()) {
                        return error(404, "not found");
                    }
                    storedPath = share.getString("stored_path");
                    expectedHash = share.getString("delete_token_hash");
                }
            }

            // 2. Only the uploader (holder of the delete token) may delete.
            if (deleteToken == null || deleteToken.isBlank()) {
                return error(401, DELETE_TOKEN_HEADER + " header required");
            }
            if (expectedHash == null || !MessageDigest.isEqual(
                    expectedHash.getBytes(StandardCharsets.US_ASCII),
                    sha256Hex(deleteToken).getBytes(StandardCharsets.US_ASCII))) {
                return error(403, "invalid delete token");
            }

            // 3. Remove the metadata row.
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM shares WHERE token = ?")) {
                del.setString(1, token);
                if (del.executeUpdate() == 0) {
                    return error(404, "not found"); // deleted concurrently
                }
            }

            // 4. Remove the bytes from disk.
            deleteQuietly(Paths.get(storedPath));

            return ResponseEntity.ok(Map.of("status", "deleted"));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** True if expires_at is set and is now or in the past (UTC). Accepts any ISO local date-time. */
    private static boolean isExpired(String expiresAt) {
        if (expiresAt == null) {
            return false;
        }
        return !LocalDateTime.now(ZoneOffset.UTC).isBefore(LocalDateTime.parse(expiresAt));
    }

    /** Uses the uploader's MIME type if it is valid, otherwise application/octet-stream. */
    private static MediaType parseContentType(String contentType) {
        if (contentType == null) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            logger.warn("Failed to delete file {}", path);
        }
    }
}
