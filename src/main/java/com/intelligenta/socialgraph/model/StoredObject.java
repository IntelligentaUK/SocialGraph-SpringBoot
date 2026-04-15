package com.intelligenta.socialgraph.model;

/**
 * Metadata for an object uploaded through the active storage provider.
 */
public record StoredObject(
    String provider,
    String objectKey,
    String objectUrl,
    String contentType
) {
}
