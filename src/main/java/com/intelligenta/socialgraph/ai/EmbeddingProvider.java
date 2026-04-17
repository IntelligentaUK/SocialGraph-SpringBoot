package com.intelligenta.socialgraph.ai;

import java.util.Optional;

/**
 * Converts text (and optionally an image URL) into a dense float vector for
 * vector search. Each active install resolves one concrete implementation
 * (sidecar, or any Spring AI-backed provider) via {@code ai.embedding.provider}
 * in {@code application.yml}. Callers should NOT depend on the concrete class.
 */
public interface EmbeddingProvider {

    /**
     * Embed plain text into an L2-normalised vector of length {@link #vectorDim()}.
     */
    float[] embedText(String text);

    /**
     * Embed an image together with a text description into a shared embedding
     * space. Returns empty when the active provider cannot do multimodal
     * embedding in a shared image+text space; the caller then falls back to
     * {@link #embedText(String)} of the description.
     */
    default Optional<float[]> embedImageAndText(String imageUrl, String textDescription) {
        return Optional.empty();
    }

    /** Output vector dimensionality for this provider + model combination. */
    int vectorDim();

    /**
     * Stable provider identifier used to namespace the RediSearch index and
     * key prefix. Must match the {@code ai.embedding.provider} value that
     * selects this bean.
     */
    String providerKey();
}
