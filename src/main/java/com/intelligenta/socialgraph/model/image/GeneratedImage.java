package com.intelligenta.socialgraph.model.image;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One generated image. Exactly one of {@code url} or {@code base64} is set —
 * OpenAI DALL-E returns URLs by default; Stability AI returns base64 payloads.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GeneratedImage(String url, String base64, String mimeType) {
}
