package com.intelligenta.socialgraph.service.storage;

import com.intelligenta.socialgraph.config.StorageProperties;
import com.intelligenta.socialgraph.model.StorageUploadTarget;
import com.intelligenta.socialgraph.model.StoredObject;
import com.intelligenta.socialgraph.util.Util;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.springframework.web.util.UriUtils.encodePath;

/**
 * Shared helpers for object-key generation and DTO creation.
 */
public abstract class AbstractObjectStorageService implements ObjectStorageService {

    private final StorageProperties properties;

    protected AbstractObjectStorageService(StorageProperties properties) {
        this.properties = properties;
    }

    protected StorageProperties getProperties() {
        return properties;
    }

    protected String nextObjectKey(String extension) {
        String normalizedPrefix = normalizePrefix(properties.getObjectKeyPrefix());
        String normalizedExtension = normalizeExtension(extension);
        return normalizedPrefix + Util.UUID() + normalizedExtension;
    }

    protected String encodeObjectKey(String objectKey) {
        return encodePath(objectKey, StandardCharsets.UTF_8);
    }

    protected StorageUploadTarget buildUploadTarget(String objectKey,
                                                    String objectUrl,
                                                    String uploadUrl,
                                                    Map<String, String> headers) {
        return new StorageUploadTarget(
            provider(),
            objectKey,
            objectUrl,
            uploadUrl,
            "PUT",
            headers,
            properties.getSignedUrlTtlSeconds()
        );
    }

    protected StoredObject buildStoredObject(String objectKey, String objectUrl, String contentType) {
        return new StoredObject(provider(), objectKey, objectUrl, contentType);
    }

    private String normalizePrefix(String prefix) {
        if (!StringUtils.hasText(prefix)) {
            return "";
        }
        String trimmed = prefix.trim();
        return trimmed.endsWith("/") ? trimmed : trimmed + "/";
    }

    private String normalizeExtension(String extension) {
        if (!StringUtils.hasText(extension)) {
            return "";
        }
        return extension.startsWith(".") ? extension : "." + extension;
    }
}
