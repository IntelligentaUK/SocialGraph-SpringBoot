package com.intelligenta.socialgraph.model.embedding;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for the sidecar's {@code /summarize/audio} and
 * {@code /summarize/video} endpoints. Unlike {@link SummarizeRequest} these
 * accept a single media URL per call (audio/video is large, so we don't
 * multiplex like we do for images).
 */
public record SummarizeAvRequest(
    @JsonProperty("status_text") String statusText,
    @JsonProperty("media_url") String mediaUrl) {
}
