package com.intelligenta.socialgraph.model;

/**
 * Response body for the liquid-rescale upload endpoint.
 */
public record LqUploadResponse(
    String provider,
    String objectKey,
    String objectUrl,
    String mimeType
) {
}
