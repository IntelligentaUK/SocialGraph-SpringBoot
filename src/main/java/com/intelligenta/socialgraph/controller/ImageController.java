package com.intelligenta.socialgraph.controller;

import com.intelligenta.socialgraph.ai.ImageGenerator;
import com.intelligenta.socialgraph.model.image.ImageGenerationRequest;
import com.intelligenta.socialgraph.model.image.ImageGenerationResponse;
import com.intelligenta.socialgraph.model.image.ImageGenerationResult;
import com.intelligenta.socialgraph.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Generates images with the active {@link ImageGenerator} provider. Returns
 * {@code 503 service_unavailable} when {@code ai.image.provider=none} so that
 * clients can detect the feature being off without enumerating providers.
 */
@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final ImageGenerator generator;

    public ImageController(ImageGenerator generator) {
        this.generator = generator;
    }

    @PostMapping("/generate")
    public ResponseEntity<ImageGenerationResponse> generate(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @Valid ImageGenerationRequest request) {
        if (!generator.enabled()) {
            return ResponseEntity.status(503).build();
        }
        long t = System.currentTimeMillis();
        ImageGenerationResult result = generator.generate(request);
        return ResponseEntity.ok(new ImageGenerationResponse(
            result.images(),
            System.currentTimeMillis() - t,
            generator.providerKey(),
            generator.modelId()));
    }
}
