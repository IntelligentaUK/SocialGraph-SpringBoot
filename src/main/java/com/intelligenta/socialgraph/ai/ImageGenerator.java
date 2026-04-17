package com.intelligenta.socialgraph.ai;

import com.intelligenta.socialgraph.model.image.ImageGenerationRequest;
import com.intelligenta.socialgraph.model.image.ImageGenerationResult;

/**
 * Produces images from a text prompt. Backed by one of Spring AI's
 * {@code ImageModel} providers (OpenAI, Azure OpenAI, Stability AI, Zhipu,
 * Vertex AI Imagen). {@link #enabled()} returns false when the active
 * {@code ai.image.provider} is {@code none}, in which case the controller
 * surfaces 503.
 */
public interface ImageGenerator {

    ImageGenerationResult generate(ImageGenerationRequest request);

    /** False when {@code ai.image.provider=none}. */
    boolean enabled();

    /** Resolved model identifier (e.g. {@code dall-e-3}). */
    String modelId();

    /** Provider key (e.g. {@code openai}). */
    String providerKey();
}
