package com.fileshare;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * Tests for the bugs found during code review plus edge cases.
 *
 * Tests in {@link KnownBugs} are regression tests: each one reproduces a bug that
 * existed in the original code (described in its comment as "Was:") and checks
 * the fixed behaviour, so the bug can't silently come back.
 * Tests in {@link EdgeCases} cover behaviour that already worked and must keep working.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BugAndEdgeCaseTests {

    static final File DB_FILE;
    static final File UPLOAD_DIR;

    static {
        try {
            DB_FILE = File.createTempFile("fileshare-bugtest", ".db");
            DB_FILE.deleteOnExit();
            // Nest the upload dir one level deep so a "../" escape lands in a dir we own and can inspect.
            Path root = Files.createTempDirectory("fileshare-bugtest-root");
            UPLOAD_DIR = Files.createDirectories(root.resolve("uploads")).toFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("fileshare.db-path", DB_FILE::getAbsolutePath);
        registry.add("fileshare.upload-dir", UPLOAD_DIR::getAbsolutePath);
        // Fixed public origin, so tests can prove share_url ignores the Host header.
        registry.add("fileshare.base-url", () -> BASE_URL);
        // Tiny upload limit so the size check can be tested with small files.
        registry.add("spring.servlet.multipart.max-file-size", () -> MAX_UPLOAD_BYTES + "B");
        // Keep the scheduled cleanup out of the way; tests call purgeDeadShares() directly.
        registry.add("fileshare.cleanup-interval", () -> "PT1H");
    }

    static final String BASE_URL = "https://files.example.test";
    static final int MAX_UPLOAD_BYTES = 1024;

    /** Delete tokens returned by uploads in the current test, by share token. */
    final Map<String, String> deleteTokens = new HashMap<>();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    Database database;

    @Autowired
    ShareCleanup cleanup;

    final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void clean() throws Exception {
        try (Connection conn = database.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM shares");
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Builds a text/plain multipart part named "file" (the form field the controller expects). */
    MockMultipartFile file(String name, String content) {
        return new MockMultipartFile("file", name, "text/plain", content.getBytes());
    }

    /**
     * Uploads a file and returns the parsed JSON response; asserts 201.
     * Remembers the returned delete_token so deleteShare() can use it.
     */
    @SuppressWarnings("unchecked")
    Map<String, Object> upload(RequestBuilder req) throws Exception {
        MvcResult result = mockMvc.perform(req).andReturn();
        assertThat(result.getResponse().getStatus()).as("upload status").isEqualTo(201);
        Map<String, Object> body = mapper.readValue(result.getResponse().getContentAsString(), Map.class);
        deleteTokens.put((String) body.get("token"), (String) body.get("delete_token"));
        return body;
    }

    /** DELETE request for a share uploaded in this test, authorized with its delete token. */
    RequestBuilder deleteShare(String token) {
        return delete("/" + token).header(FileController.DELETE_TOKEN_HEADER, deleteTokens.get(token));
    }

    /** Overwrites a share's columns directly in the DB (to simulate time passing / usage). */
    void setColumn(String token, String column, Object value) throws Exception {
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE shares SET " + column + " = ? WHERE token = ?")) {
            stmt.setObject(1, value);
            stmt.setString(2, token);
            stmt.executeUpdate();
        }
    }

    /** Number of share rows currently in the DB. */
    int shareCount() throws Exception {
        try (Connection conn = database.getConnection(); Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM shares")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Uploads a file with default expiry/limit and returns just its share token. */
    String uploadToken(String name, String content) throws Exception {
        return (String) upload(multipart("/upload").file(file(name, content))).get("token");
    }

    /**
     * Performs a request and returns its HTTP status. An unhandled exception
     * thrown by the controller (which would be a 500 in production) is reported as 500.
     */
    int statusOf(RequestBuilder req) {
        try {
            return mockMvc.perform(req).andReturn().getResponse().getStatus();
        } catch (Exception e) {
            return 500;
        }
    }

    /** Reads the on-disk path of a share straight from the DB (not exposed by the API). */
    String storedPath(String token) throws Exception {
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT stored_path FROM shares WHERE token = ?")) {
            stmt.setString(1, token);
            try (ResultSet rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getString(1);
            }
        }
    }

    /** Calls GET /info/{token} and returns the parsed JSON metadata. */
    @SuppressWarnings("unchecked")
    Map<String, Object> info(String token) throws Exception {
        MvcResult r = mockMvc.perform(get("/info/" + token)).andReturn();
        return mapper.readValue(r.getResponse().getContentAsString(), Map.class);
    }

    // ============================================================ known bugs

    @Nested
    class KnownBugs {

        /** Was: expires_at was stored but never checked. Now: an expired share returns 410. */
        @Test
        void expiredShareCannotBeDownloaded() throws Exception {
            String token = uploadToken("old.txt", "data");
            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("UPDATE shares SET expires_at = ? WHERE token = ?")) {
                stmt.setString(1, LocalDateTime.now(ZoneOffset.UTC).minusHours(1).toString());
                stmt.setString(2, token);
                stmt.executeUpdate();
            }

            assertThat(statusOf(get("/" + token))).isEqualTo(410);
        }

        /** Was: the client filename was used as the disk path, so "../" escaped the upload dir. Now: files are stored as <uploadDir>/<token>. */
        @Test
        void pathTraversalFilenameStaysInsideUploadDir() throws Exception {
            File escaped = new File(UPLOAD_DIR.getParentFile(), "escaped.txt");
            Files.deleteIfExists(escaped.toPath());
            try {
                int status = statusOf(multipart("/upload").file(file("../escaped.txt", "pwned")));

                assertThat(escaped).as("file written outside upload dir").doesNotExist();
                // Either rejecting the name (400) or sanitizing it (201) is acceptable.
                assertThat(status).isIn(201, 400);
            } finally {
                Files.deleteIfExists(escaped.toPath());
            }
        }

        /** Was: same-named uploads shared one file on disk and the second overwrote the first. Now: one file per token. */
        @Test
        void sameFilenameUploadsDoNotOverwriteEachOther() throws Exception {
            String first = uploadToken("report.txt", "first version");
            String second = uploadToken("report.txt", "second version");

            MvcResult r1 = mockMvc.perform(get("/" + first)).andReturn();
            MvcResult r2 = mockMvc.perform(get("/" + second)).andReturn();

            assertThat(r1.getResponse().getContentAsString()).isEqualTo("first version");
            assertThat(r2.getResponse().getContentAsString()).isEqualTo("second version");
        }

        /** Was: deleting one share removed the file another same-named share still used. Now: each share owns its own file. */
        @Test
        void deletingOneShareKeepsOtherSharesWithSameFilename() throws Exception {
            String keep = uploadToken("shared-name.txt", "keep me");
            String remove = uploadToken("shared-name.txt", "remove me");

            assertThat(statusOf(deleteShare(remove))).isEqualTo(200);

            MvcResult r = mockMvc.perform(get("/" + keep)).andReturn();
            assertThat(r.getResponse().getStatus()).isEqualTo(200);
            assertThat(r.getResponse().getContentAsString()).isEqualTo("keep me");
        }

        /**
         * Was: download_count was read, checked, then written in separate steps, so
         * concurrent requests could all pass the check. Now: a single conditional UPDATE
         * (download_count < max_downloads) enforces the limit atomically.
         */
        @Test
        void concurrentDownloadsDoNotExceedMaxDownloads() throws Exception {
            String token = (String) upload(multipart("/upload")
                    .file(file("race.txt", "data"))
                    .param("max_downloads", "1")).get("token");

            int threads = 16;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return statusOf(get("/" + token));
                }));
            }
            start.countDown();

            int ok = 0;
            for (Future<Integer> f : results) {
                if (f.get() == 200) ok++;
            }
            pool.shutdown();

            assertThat(ok).as("successful downloads with max_downloads=1").isEqualTo(1);
            assertThat(info(token).get("download_count")).isEqualTo(1);
        }

        /** Was: zero/negative limits were accepted, creating shares dead on arrival. Now: 400. */
        @Test
        void nonPositiveMaxDownloadsIsRejected() {
            assertThat(statusOf(multipart("/upload").file(file("a.txt", "x")).param("max_downloads", "0"))).isEqualTo(400);
            assertThat(statusOf(multipart("/upload").file(file("a.txt", "x")).param("max_downloads", "-1"))).isEqualTo(400);
        }

        @Test
        void nonPositiveExpiryIsRejected() {
            assertThat(statusOf(multipart("/upload").file(file("a.txt", "x")).param("expires_in_hours", "0"))).isEqualTo(400);
            assertThat(statusOf(multipart("/upload").file(file("a.txt", "x")).param("expires_in_hours", "-5"))).isEqualTo(400);
        }

        /** Was: a quote broke the hand-built Content-Disposition header (and crashed the upload on Windows). Now: Spring ContentDisposition encodes it. */
        @Test
        void filenameWithQuoteProducesValidContentDisposition() throws Exception {
            String token = uploadToken("my \"quoted\" file.txt", "data");

            String header = mockMvc.perform(get("/" + token)).andReturn()
                    .getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);

            assertThat(ContentDisposition.parse(header).getFilename()).isEqualTo("my \"quoted\" file.txt");
        }

        /** Was: CR/LF in the filename was copied into the response header (header injection). Now: control chars are stripped. */
        @Test
        void filenameWithNewlineCannotInjectHeaders() throws Exception {
            String token = uploadToken("evil.txt\r\nSet-Cookie: session=hijacked", "data");

            String header = mockMvc.perform(get("/" + token)).andReturn()
                    .getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);

            assertThat(header).doesNotContain("\r").doesNotContain("\n");
        }

        /** Was: a file missing on disk gave a 500 and still used up a download. Now: 404, count unchanged. */
        @Test
        void missingFileOnDiskReturnsCleanErrorAndDoesNotCountDownload() throws Exception {
            String token = uploadToken("vanished.txt", "data");
            Files.delete(Path.of(storedPath(token)));

            assertThat(statusOf(get("/" + token))).isIn(404, 410);
            assertThat(info(token).get("download_count")).isEqualTo(0);
        }

        /** Was: an empty filename resolved to the upload dir itself and crashed with 500. Now: stored under the token, named "file". */
        @Test
        void emptyFilenameDoesNotCauseServerError() {
            int status = statusOf(multipart("/upload").file(file("", "data")));
            assertThat(status).isIn(201, 400);
        }

        /** Was: 6 chars from java.util.Random were guessable. Now: 128-bit SecureRandom tokens (22 chars). */
        @Test
        void tokensAreLongAndUnique() throws Exception {
            Set<String> tokens = new HashSet<>();
            for (int i = 0; i < 50; i++) {
                tokens.add(uploadToken("t" + i + ".txt", "x"));
            }
            assertThat(tokens).hasSize(50);
            assertThat(tokens).allSatisfy(t -> assertThat(t.length()).isGreaterThanOrEqualTo(16));
        }
    }

    // ============================================================ edge cases

    @Nested
    class EdgeCases {

        /** A request with params but no "file" part is a client error. */
        @Test
        void uploadWithoutFileReturns400() {
            assertThat(statusOf(multipart("/upload").param("max_downloads", "3"))).isEqualTo(400);
        }

        /** Spring's int binding should turn "abc" into a 400, not a 500. */
        @Test
        void nonNumericParamsReturn400() {
            assertThat(statusOf(multipart("/upload").file(file("a.txt", "x")).param("max_downloads", "abc"))).isEqualTo(400);
            assertThat(statusOf(multipart("/upload").file(file("a.txt", "x")).param("expires_in_hours", "soon"))).isEqualTo(400);
        }

        /** Omitting the optional params gives max_downloads=10 and expiry ~24h from now (±1 min). */
        @Test
        void defaultsAreTenDownloadsAndTwentyFourHours() throws Exception {
            LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);
            Map<String, Object> body = upload(multipart("/upload").file(file("d.txt", "x")));

            assertThat(body.get("max_downloads")).isEqualTo(10);
            LocalDateTime expires = LocalDateTime.parse((String) body.get("expires_at"));
            assertThat(expires).isBetween(before.plusHours(24).minusMinutes(1), before.plusHours(24).plusMinutes(1));
        }

        /** The share_url returned to the uploader must point at the download endpoint for this token. */
        @Test
        void uploadResponseContainsShareUrlEndingWithToken() throws Exception {
            Map<String, Object> body = upload(multipart("/upload").file(file("u.txt", "x")));
            assertThat((String) body.get("share_url")).endsWith("/" + body.get("token"));
        }

        /** Every byte value 0..255 survives upload + download unchanged (no text/encoding mangling). */
        @Test
        void binaryContentRoundTripsExactly() throws Exception {
            byte[] bytes = new byte[256];
            for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) i;
            String token = (String) upload(multipart("/upload")
                    .file(new MockMultipartFile("file", "blob.bin", "application/octet-stream", bytes))).get("token");

            byte[] downloaded = mockMvc.perform(get("/" + token)).andReturn().getResponse().getContentAsByteArray();
            assertThat(downloaded).isEqualTo(bytes);
        }

        /** Viewing metadata is read-only and must not use up downloads. */
        @Test
        void infoDoesNotCountAsDownload() throws Exception {
            String token = uploadToken("i.txt", "x");
            info(token);
            info(token);
            assertThat(info(token).get("download_count")).isEqualTo(0);
        }

        /** Each successful download adds 1 to download_count, visible via /info. */
        @Test
        void downloadIncrementsCountShownInInfo() throws Exception {
            String token = uploadToken("c.txt", "x");
            mockMvc.perform(get("/" + token));
            mockMvc.perform(get("/" + token));
            assertThat(info(token).get("download_count")).isEqualTo(2);
        }

        /** /info reports the real byte size and the MIME type the client sent. */
        @Test
        void infoReportsSizeAndContentType() throws Exception {
            String token = uploadToken("s.txt", "12345");
            Map<String, Object> body = info(token);
            assertThat(body.get("size_bytes")).isEqualTo(5);
            assertThat(body.get("content_type")).isEqualTo("text/plain");
        }

        /** Boundary: a limit of 1 allows the first download and returns 410 Gone on the second. */
        @Test
        void maxDownloadsOfOneAllowsExactlyOneDownload() throws Exception {
            String token = (String) upload(multipart("/upload")
                    .file(file("one.txt", "x")).param("max_downloads", "1")).get("token");
            assertThat(statusOf(get("/" + token))).isEqualTo(200);
            assertThat(statusOf(get("/" + token))).isEqualTo(410);
        }

        /** DELETE must remove the bytes from disk, not just the DB row (no orphaned files). */
        @Test
        void deleteRemovesFileFromDisk() throws Exception {
            String token = uploadToken("gone.txt", "x");
            Path path = Path.of(storedPath(token));
            assertThat(path).exists();

            mockMvc.perform(deleteShare(token));
            assertThat(path).doesNotExist();
        }

        /** A deleted share's link stops working. */
        @Test
        void downloadAfterDeleteReturns404() throws Exception {
            String token = uploadToken("x.txt", "x");
            mockMvc.perform(deleteShare(token));
            assertThat(statusOf(get("/" + token))).isEqualTo(404);
        }

        /** Deleting an already-deleted share returns 404 rather than erroring. */
        @Test
        void deletingTwiceReturns404() throws Exception {
            String token = uploadToken("twice.txt", "x");
            assertThat(statusOf(deleteShare(token))).isEqualTo(200);
            assertThat(statusOf(deleteShare(token))).isEqualTo(404);
        }

        /** download, info and delete all return 404 for a token that never existed. */
        @Test
        void unknownTokenReturns404OnEveryEndpoint() {
            assertThat(statusOf(get("/doesnotexist"))).isEqualTo(404);
            assertThat(statusOf(get("/info/doesnotexist"))).isEqualTo(404);
            assertThat(statusOf(delete("/doesnotexist"))).isEqualTo(404);
        }
    }

    // ============================================================ hardening

    /**
     * Production-readiness changes: delete authorization, trusted share URLs,
     * upload size limit, and cleanup of dead shares.
     */
    @Nested
    class Hardening {

        // ---- delete token: only the uploader can delete

        /** Upload returns a delete_token that is different from the public share token. */
        @Test
        void uploadReturnsSeparateDeleteToken() throws Exception {
            Map<String, Object> body = upload(multipart("/upload").file(file("a.txt", "x")));
            assertThat((String) body.get("delete_token")).isNotBlank().isNotEqualTo(body.get("token"));
        }

        /** Knowing the share link is not enough to delete: no header -> 401, share untouched. */
        @Test
        void deleteWithoutDeleteTokenReturns401() throws Exception {
            String token = uploadToken("a.txt", "x");
            assertThat(statusOf(delete("/" + token))).isEqualTo(401);
            assertThat(statusOf(get("/info/" + token))).isEqualTo(200);
        }

        /** A wrong delete token (here: the share token itself) -> 403, share untouched. */
        @Test
        void deleteWithWrongDeleteTokenReturns403() throws Exception {
            String token = uploadToken("a.txt", "x");
            assertThat(statusOf(delete("/" + token).header(FileController.DELETE_TOKEN_HEADER, token))).isEqualTo(403);
            assertThat(statusOf(get("/info/" + token))).isEqualTo(200);
        }

        /** One share's delete token can't delete a different share. */
        @Test
        void deleteTokenOnlyWorksForItsOwnShare() throws Exception {
            String a = uploadToken("a.txt", "x");
            String b = uploadToken("b.txt", "y");
            assertThat(statusOf(delete("/" + b).header(FileController.DELETE_TOKEN_HEADER, deleteTokens.get(a))))
                    .isEqualTo(403);
        }

        /** The delete token never leaks through /info, and the DB stores only its hash. */
        @Test
        void deleteTokenIsNotExposedOrStoredInPlaintext() throws Exception {
            String token = uploadToken("a.txt", "x");
            String deleteToken = deleteTokens.get(token);

            String infoJson = mockMvc.perform(get("/info/" + token)).andReturn().getResponse().getContentAsString();
            assertThat(infoJson).doesNotContain(deleteToken).doesNotContain("delete_token");

            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT delete_token_hash FROM shares WHERE token = ?")) {
                stmt.setString(1, token);
                try (ResultSet rs = stmt.executeQuery()) {
                    rs.next();
                    assertThat(rs.getString(1)).isEqualTo(FileController.sha256Hex(deleteToken)).isNotEqualTo(deleteToken);
                }
            }
        }

        // ---- share_url comes from config, not the Host header

        /** A spoofed Host header must not change the share link handed back to the uploader. */
        @Test
        void shareUrlUsesConfiguredBaseUrlNotHostHeader() throws Exception {
            Map<String, Object> body = upload(multipart("/upload").file(file("a.txt", "x"))
                    .header(HttpHeaders.HOST, "evil.example.com"));
            assertThat((String) body.get("share_url")).isEqualTo(BASE_URL + "/" + body.get("token"));
        }

        // ---- upload size limit

        /** Over the limit -> 413, and nothing is written to disk or the DB. */
        @Test
        void oversizedUploadReturns413AndStoresNothing() throws Exception {
            long filesBefore = countFiles(UPLOAD_DIR.toPath());
            byte[] tooBig = new byte[MAX_UPLOAD_BYTES + 1];

            int status = statusOf(multipart("/upload")
                    .file(new MockMultipartFile("file", "big.bin", "application/octet-stream", tooBig)));

            assertThat(status).isEqualTo(413);
            assertThat(shareCount()).isZero();
            assertThat(countFiles(UPLOAD_DIR.toPath())).isEqualTo(filesBefore);
        }

        /** Boundary: a file of exactly the limit is accepted. */
        @Test
        void uploadAtExactSizeLimitIsAccepted() {
            byte[] exact = new byte[MAX_UPLOAD_BYTES];
            assertThat(statusOf(multipart("/upload")
                    .file(new MockMultipartFile("file", "edge.bin", "application/octet-stream", exact)))).isEqualTo(201);
        }

        // ---- cleanup of dead shares

        /** Expired shares lose both their DB row and their file. */
        @Test
        void cleanupRemovesExpiredSharesAndFiles() throws Exception {
            String token = uploadToken("old.txt", "x");
            Path path = Path.of(storedPath(token));
            setColumn(token, "expires_at", "2000-01-01T00:00:00");

            assertThat(cleanup.purgeDeadShares()).isEqualTo(1);
            assertThat(statusOf(get("/info/" + token))).isEqualTo(404);
            assertThat(path).doesNotExist();
        }

        /** Shares that used up max_downloads are removed too. */
        @Test
        void cleanupRemovesExhaustedShares() throws Exception {
            String token = (String) upload(multipart("/upload")
                    .file(file("once.txt", "x")).param("max_downloads", "1")).get("token");
            mockMvc.perform(get("/" + token));

            assertThat(cleanup.purgeDeadShares()).isEqualTo(1);
            assertThat(statusOf(get("/info/" + token))).isEqualTo(404);
        }

        /** Shares that can still be downloaded are left alone. */
        @Test
        void cleanupKeepsActiveShares() throws Exception {
            String token = uploadToken("fresh.txt", "x");
            mockMvc.perform(get("/" + token)); // 1 of 10 downloads used

            assertThat(cleanup.purgeDeadShares()).isZero();
            assertThat(statusOf(get("/" + token))).isEqualTo(200);
        }

        /** More dead shares than one batch (500) are all removed in a single run. */
        @Test
        void cleanupHandlesMoreThanOneBatch() throws Exception {
            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("""
                     INSERT INTO shares (token, filename, stored_path, expires_at, max_downloads)
                     VALUES (?, 'f', ?, '2000-01-01T00:00:00', 1)
                 """)) {
                for (int i = 0; i < 1200; i++) {
                    stmt.setString(1, "dead" + i);
                    stmt.setString(2, UPLOAD_DIR + File.separator + "dead" + i); // file doesn't exist; that's fine
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }

            assertThat(cleanup.purgeDeadShares()).isEqualTo(1200);
            assertThat(shareCount()).isZero();
        }

        private long countFiles(Path dir) throws IOException {
            try (var files = Files.list(dir)) {
                return files.count();
            }
        }
    }
}
