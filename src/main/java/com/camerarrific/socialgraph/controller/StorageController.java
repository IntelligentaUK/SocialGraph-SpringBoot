package com.camerarrific.socialgraph.controller;

import com.camerarrific.socialgraph.security.UserPrincipal;
import com.camerarrific.socialgraph.service.BlobStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for storage-related endpoints.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Storage", description = "Blob storage endpoints")
@SecurityRequirement(name = "bearerAuth")
public class StorageController {

    private final BlobStorageService blobStorageService;

    @PostMapping("/request/storage/key")
    @Operation(summary = "Request Storage Key", description = "Get a SAS key for direct blob upload")
    public ResponseEntity<Map<String, Object>> requestStorageKey(
            @AuthenticationPrincipal UserPrincipal principal) {
        
        log.debug("Storage key requested by user: {}", principal.getUid());
        Map<String, Object> sasKey = blobStorageService.generateSasKey();
        return ResponseEntity.ok(sasKey);
    }

    @PostMapping("/upload")
    @Operation(summary = "Upload Media", description = "Upload media directly to blob storage")
    public ResponseEntity<Map<String, String>> uploadMedia(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody byte[] data) {
        
        log.debug("Upload requested by user: {}, size: {} bytes", principal.getUid(), data.length);
        String url = blobStorageService.upload(data);
        
        if (url != null) {
            return ResponseEntity.ok(Map.of("url", url));
        } else {
            return ResponseEntity.internalServerError().body(Map.of("error", "Upload failed"));
        }
    }
}

