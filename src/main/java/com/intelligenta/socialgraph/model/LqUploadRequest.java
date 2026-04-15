package com.intelligenta.socialgraph.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request body for the liquid-rescale upload endpoint.
 */
public record LqUploadRequest(
    @NotBlank String imageBase64,
    @NotNull @Positive Integer cut,
    String mimeType
) {
}
