package com.intelligenta.socialgraph.model;

import java.util.Map;

/**
 * Provider-neutral signed upload target details.
 */
public record StorageUploadTarget(
    String provider,
    String objectKey,
    String objectUrl,
    String uploadUrl,
    String method,
    Map<String, String> headers,
    long expiresIn
) {
    public StorageUploadTarget {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
