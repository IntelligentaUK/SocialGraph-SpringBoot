package com.intelligenta.socialgraph.model.image;

import java.util.List;

/**
 * Internal result returned by {@link com.intelligenta.socialgraph.ai.ImageGenerator#generate}.
 * The controller wraps this into {@link ImageGenerationResponse} adding the
 * active provider / model metadata.
 */
public record ImageGenerationResult(List<GeneratedImage> images) {
}
