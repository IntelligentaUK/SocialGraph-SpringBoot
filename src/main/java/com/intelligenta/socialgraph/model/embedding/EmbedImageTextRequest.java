package com.intelligenta.socialgraph.model.embedding;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EmbedImageTextRequest(
    @JsonProperty("image_url") String imageUrl,
    @JsonProperty("text_description") String textDescription) {
}
