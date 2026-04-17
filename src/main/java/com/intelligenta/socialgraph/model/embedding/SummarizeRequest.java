package com.intelligenta.socialgraph.model.embedding;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SummarizeRequest(
    @JsonProperty("status_text") String statusText,
    @JsonProperty("image_urls") List<String> imageUrls) {
}
