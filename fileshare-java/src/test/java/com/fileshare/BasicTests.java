package com.fileshare;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Original happy-path tests: one per endpoint plus the download limit.
 *
 * Boots the full Spring app (@SpringBootTest) and drives it through MockMvc,
 * which calls the controllers in-process without opening a real HTTP port.
 * See BugAndEdgeCaseTests for bug regressions and edge cases.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BasicTests {

    // Throwaway SQLite file + upload dir so tests never touch the real fileshare.db / uploads/.
    static final File DB_FILE;
    static final File UPLOAD_DIR;

    static {
        try {
            DB_FILE = File.createTempFile("fileshare-test", ".db");
            DB_FILE.deleteOnExit();
            UPLOAD_DIR = Files.createTempDirectory("fileshare-uploads").toFile();
            UPLOAD_DIR.deleteOnExit();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Points the app's fileshare.db-path / fileshare.upload-dir properties at the temp locations above. */
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("fileshare.db-path", DB_FILE::getAbsolutePath);
        registry.add("fileshare.upload-dir", UPLOAD_DIR::getAbsolutePath);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Database database;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Empties the shares table before each test so tests don't see each other's data. */
    @BeforeEach
    void clean() throws Exception {
        try (Connection conn = database.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM shares");
        }
    }

    /** POST /upload with a file returns 201 and a JSON body containing the share token. */
    @Test
    void uploadReturnsToken() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/upload")
                .file(new MockMultipartFile("file", "hello.txt", "text/plain", "hello world".getBytes())))
                .andExpect(status().isCreated())
                .andReturn();
        Map<String, Object> body = mapper.readValue(result.getResponse().getContentAsString(), Map.class);
        assertThat(body).containsKey("token");
    }

    /** Upload, then GET /{token} returns 200 with exactly the uploaded bytes. */
    @Test
    void downloadReturnsFile() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/upload")
                .file(new MockMultipartFile("file", "hello.txt", "text/plain", "hello world".getBytes())))
                .andExpect(status().isCreated())
                .andReturn();
        String token = (String) mapper.readValue(result.getResponse().getContentAsString(), Map.class).get("token");

        MvcResult download = mockMvc.perform(get("/" + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(download.getResponse().getContentAsByteArray()).isEqualTo("hello world".getBytes());
    }

    /** GET /info/{token} returns 200 and the original filename in the metadata. */
    @Test
    void infoReturnsMetadata() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/upload")
                .file(new MockMultipartFile("file", "hello.txt", "text/plain", "hello".getBytes())))
                .andExpect(status().isCreated())
                .andReturn();
        String token = (String) mapper.readValue(result.getResponse().getContentAsString(), Map.class).get("token");

        MvcResult info = mockMvc.perform(get("/info/" + token))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> body = mapper.readValue(info.getResponse().getContentAsString(), Map.class);
        assertThat(body.get("filename")).isEqualTo("hello.txt");
    }

    /** With max_downloads=2: downloads 1 and 2 succeed, download 3 returns 410 Gone. */
    @Test
    void maxDownloadsEnforced() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/upload")
                .file(new MockMultipartFile("file", "test.txt", "text/plain", "data".getBytes()))
                .param("max_downloads", "2"))
                .andExpect(status().isCreated())
                .andReturn();
        String token = (String) mapper.readValue(result.getResponse().getContentAsString(), Map.class).get("token");

        mockMvc.perform(get("/" + token)).andExpect(status().isOk());
        mockMvc.perform(get("/" + token)).andExpect(status().isOk());
        mockMvc.perform(get("/" + token)).andExpect(status().isGone());
    }

    /**
     * DELETE /{token} with the delete_token from the upload response (sent in the
     * X-Delete-Token header) returns 200, after which /info for that token is 404.
     */
    @Test
    void deleteRemovesShare() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/upload")
                .file(new MockMultipartFile("file", "test.txt", "text/plain", "data".getBytes())))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> body = mapper.readValue(result.getResponse().getContentAsString(), Map.class);
        String token = (String) body.get("token");
        String deleteToken = (String) body.get("delete_token");

        mockMvc.perform(delete("/" + token).header("X-Delete-Token", deleteToken)).andExpect(status().isOk());
        mockMvc.perform(get("/info/" + token)).andExpect(status().isNotFound());
    }

    /** Downloading a token that was never issued returns 404. */
    @Test
    void unknownTokenReturns404() throws Exception {
        mockMvc.perform(get("/notreal")).andExpect(status().isNotFound());
    }
}
