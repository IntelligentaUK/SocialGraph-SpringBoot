package com.intelligenta.socialgraph.ai.embedding;

import com.intelligenta.socialgraph.ai.EmbeddingProvider;
import com.intelligenta.socialgraph.ai.ProviderException;

/**
 * Placeholder used when {@code ai.embedding.provider=none}. Any call throws,
 * signalling that the install has no embedding provider configured.
 */
public class NoopEmbeddingProvider implements EmbeddingProvider {

    public static final String PROVIDER_KEY = "none";

    @Override
    public float[] embedText(String text) {
        throw new ProviderException("embedding provider is disabled (ai.embedding.provider=none)");
    }

    @Override
    public int vectorDim() {
        return 0;
    }

    @Override
    public String providerKey() {
        return PROVIDER_KEY;
    }
}
