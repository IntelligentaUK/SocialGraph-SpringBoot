package com.camerarrific.socialgraph.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Controller for health and status endpoints.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Health", description = "Health and status endpoints")
public class HealthController {

    @GetMapping("/ping")
    @Operation(summary = "Ping", description = "Simple health check endpoint")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("hello");
    }

    @GetMapping("/status")
    @Operation(summary = "Status", description = "Detailed status information")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "timestamp", Instant.now().toString(),
            "version", "2.0.0"
        ));
    }
}

