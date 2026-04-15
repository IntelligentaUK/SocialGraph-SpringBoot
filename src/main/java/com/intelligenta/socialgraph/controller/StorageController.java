package com.intelligenta.socialgraph.controller;

import com.intelligenta.socialgraph.LiquidRescaler;
import com.intelligenta.socialgraph.exception.SocialGraphException;
import com.intelligenta.socialgraph.model.LqUploadRequest;
import com.intelligenta.socialgraph.model.LqUploadResponse;
import com.intelligenta.socialgraph.model.StorageUploadTarget;
import com.intelligenta.socialgraph.model.StoredObject;
import com.intelligenta.socialgraph.service.storage.ObjectStorageService;
import com.intelligenta.socialgraph.util.ImagePayloads;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * Controller for storage-related endpoints.
 */
@RestController
@RequestMapping("/api")
public class StorageController {

    private final ObjectStorageService objectStorageService;

    public StorageController(ObjectStorageService objectStorageService) {
        this.objectStorageService = objectStorageService;
    }

    /**
     * Request a provider-neutral signed upload target.
     */
    @PostMapping("/request/storage/key")
    public ResponseEntity<StorageUploadTarget> requestStorageKey() {
        return ResponseEntity.ok(objectStorageService.createSignedUploadTarget(null, null));
    }

    /**
     * Upload a base64 image, seam-carve it, and store it in the active object store.
     */
    @PostMapping("/lq/upload")
    public ResponseEntity<LqUploadResponse> uploadWithRescale(@Valid @RequestBody LqUploadRequest request) {
        ImagePayloads.ImagePayload payload = ImagePayloads.fromBase64(request.imageBase64(), request.mimeType());
        LiquidRescaler.RescaledImage rescaledImage;
        try {
            rescaledImage = LiquidRescaler.rescaleImage(
                payload.bytes(),
                request.cut(),
                1,
                payload.mimeType()
            );
        } catch (IOException ex) {
            throw new SocialGraphException("invalid_image_payload", "The supplied image could not be processed");
        }
        StoredObject storedObject = objectStorageService.upload(
            rescaledImage.bytes(),
            rescaledImage.extension(),
            rescaledImage.mimeType()
        );
        return ResponseEntity.ok(new LqUploadResponse(
            storedObject.provider(),
            storedObject.objectKey(),
            storedObject.objectUrl(),
            storedObject.contentType()
        ));
    }
}
