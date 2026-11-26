package com.fileshare;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

/**
 * Converts framework exceptions into the same {"error": "..."} JSON shape the
 * controller uses, instead of Spring's default error page.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * Thrown by Spring's multipart parser when an upload exceeds
     * spring.servlet.multipart.max-file-size / max-request-size.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Object> uploadTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(413).body(Map.of("error", "file too large"));
    }
}
