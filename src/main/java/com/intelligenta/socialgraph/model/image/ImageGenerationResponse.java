package com.intelligenta.socialgraph.model.image;

import java.util.List;

public record ImageGenerationResponse(
    List<GeneratedImage> images,
    long durationMs,
    String provider,
    String model) {
}
