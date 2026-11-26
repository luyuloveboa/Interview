package com.fileshare;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of the fileshare service.
 *
 * How the pieces fit together:
 *   FileshareApplication  -> boots Spring, creates/migrates the DB schema on startup
 *   Database              -> pooled SQLite connections (HikariCP), owns the table schema
 *   FileController        -> the 4 HTTP endpoints (upload / download / info / delete)
 *   ApiExceptionHandler   -> turns framework errors (e.g. upload too large) into JSON
 *   ShareCleanup          -> scheduled job that removes expired / exhausted shares
 *
 * Data lives in two places:
 *   - file bytes  -> on disk as <upload dir>/<token> (fileshare.upload-dir, default "uploads/")
 *   - metadata    -> one row per share in the SQLite "shares" table (fileshare.db-path)
 * All settings are in src/main/resources/application.properties.
 */
@SpringBootApplication
@EnableScheduling // enables ShareCleanup's @Scheduled method
public class FileshareApplication {

    /** Starts the embedded Tomcat server (127.0.0.1:8080 by default). */
    public static void main(String[] args) {
        SpringApplication.run(FileshareApplication.class, args);
    }

    /**
     * Runs once after the Spring context is ready: makes sure the "shares"
     * table exists (and is migrated) before any request is served.
     */
    @Bean
    ApplicationRunner initDatabase(Database database) {
        return args -> database.init();
    }
}
