package com.intelligenta.socialgraph.util;

import com.intelligenta.socialgraph.exception.SocialGraphException;
import org.springframework.util.StringUtils;

import java.util.Base64;
import java.util.Locale;
import java.util.Set;

/**
 * Helpers for parsing and normalizing supported image payloads.
 */
public final class ImagePayloads {

    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of(
        "image/jpeg",
        "image/png",
        "image/webp"
    );

    private ImagePayloads() {
    }

    public static ImagePayload fromBase64(String imageBase64, String requestedMimeType) {
        if (!StringUtils.hasText(imageBase64)) {
            throw new SocialGraphException("invalid_image_payload", "imageBase64 is required");
        }

        String payload = imageBase64.trim();
        String mimeType = requestedMimeType;

        if (payload.regionMatches(true, 0, "data:", 0, 5)) {
            int commaIndex = payload.indexOf(',');
            if (commaIndex < 0) {
                throw new SocialGraphException("invalid_image_payload", "imageBase64 data URL is malformed");
            }

            String metadata = payload.substring(5, commaIndex);
            int base64Index = metadata.toLowerCase(Locale.ROOT).indexOf(";base64");
            if (base64Index < 0) {
                throw new SocialGraphException("invalid_image_payload", "imageBase64 must use base64 encoding");
            }

            String dataUrlMimeType = metadata.substring(0, base64Index);
            if (StringUtils.hasText(dataUrlMimeType)) {
                mimeType = dataUrlMimeType;
            }
            payload = payload.substring(commaIndex + 1);
        }

        try {
            byte[] bytes = Base64.getMimeDecoder().decode(payload);
            return fromBytes(bytes, mimeType);
        } catch (IllegalArgumentException ex) {
            throw new SocialGraphException("invalid_image_payload", "imageBase64 must be valid base64");
        }
    }

    public static ImagePayload fromBytes(byte[] bytes, String requestedMimeType) {
        if (bytes == null || bytes.length == 0) {
            throw new SocialGraphException("invalid_image_payload", "Image bytes are required");
        }

        String normalizedRequestedMimeType = normalizeRequestedMimeType(requestedMimeType);
        String detectedMimeType = detectMimeType(bytes);

        String resolvedMimeType = detectedMimeType != null ? detectedMimeType : normalizedRequestedMimeType;
        if (resolvedMimeType == null) {
            throw new SocialGraphException(
                "unsupported_image_type",
                "Only JPEG, PNG, and WebP images are supported"
            );
        }

        return new ImagePayload(bytes, resolvedMimeType, extensionForMimeType(resolvedMimeType));
    }

    public static String extensionForMimeType(String mimeType) {
        return switch (normalizeMimeType(mimeType)) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new SocialGraphException(
                "unsupported_image_type",
                "Only JPEG, PNG, and WebP images are supported"
            );
        };
    }

    public static String normalizeMimeType(String mimeType) {
        if (!StringUtils.hasText(mimeType)) {
            return null;
        }

        String normalized = mimeType.trim().toLowerCase(Locale.ROOT);
        int parameterIndex = normalized.indexOf(';');
        if (parameterIndex >= 0) {
            normalized = normalized.substring(0, parameterIndex);
        }

        return switch (normalized) {
            case "image/jpg" -> "image/jpeg";
            case "image/x-png" -> "image/png";
            case "image/x-webp" -> "image/webp";
            default -> normalized;
        };
    }

    public static String detectMimeType(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return null;
        }
        if (isJpeg(bytes)) {
            return "image/jpeg";
        }
        if (isPng(bytes)) {
            return "image/png";
        }
        if (isWebp(bytes)) {
            return "image/webp";
        }
        return null;
    }

    private static String normalizeRequestedMimeType(String requestedMimeType) {
        String normalized = normalizeMimeType(requestedMimeType);
        if (!StringUtils.hasText(normalized) || "application/octet-stream".equals(normalized)) {
            return null;
        }
        if (!SUPPORTED_MIME_TYPES.contains(normalized)) {
            throw new SocialGraphException(
                "unsupported_image_type",
                "Only JPEG, PNG, and WebP images are supported"
            );
        }
        return normalized;
    }

    private static boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3
            && (bytes[0] & 0xFF) == 0xFF
            && (bytes[1] & 0xFF) == 0xD8
            && (bytes[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] bytes) {
        return bytes.length >= 8
            && (bytes[0] & 0xFF) == 0x89
            && bytes[1] == 'P'
            && bytes[2] == 'N'
            && bytes[3] == 'G'
            && (bytes[4] & 0xFF) == 0x0D
            && (bytes[5] & 0xFF) == 0x0A
            && (bytes[6] & 0xFF) == 0x1A
            && (bytes[7] & 0xFF) == 0x0A;
    }

    private static boolean isWebp(byte[] bytes) {
        return bytes.length >= 12
            && bytes[0] == 'R'
            && bytes[1] == 'I'
            && bytes[2] == 'F'
            && bytes[3] == 'F'
            && bytes[8] == 'W'
            && bytes[9] == 'E'
            && bytes[10] == 'B'
            && bytes[11] == 'P';
    }

    public record ImagePayload(byte[] bytes, String mimeType, String extension) {
    }
}
