package com.intelligenta.socialgraph.ai.embedding;

import com.intelligenta.socialgraph.ai.DefaultModelCatalog;
import com.intelligenta.socialgraph.ai.EmbeddingProvider;
import com.intelligenta.socialgraph.ai.ProviderException;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.Optional;

/**
 * Provider-agnostic {@link EmbeddingProvider} backed by whichever
 * {@link EmbeddingModel} Spring AI's autoconfig produced for the active
 * {@code ai.embedding.provider}. The concrete bean class varies by provider
 * (OpenAi, Azure, Vertex, Ollama, ...) but the interface is uniform.
 */
public class SpringAiEmbeddingProvider implements EmbeddingProvider {

    private final EmbeddingModel model;
    private final String providerKey;
    private final int vectorDim;
    private final boolean multimodal;

    public SpringAiEmbeddingProvider(EmbeddingModel model, String providerKey, int vectorDim) {
        this.model = model;
        this.providerKey = providerKey;
        this.vectorDim = vectorDim;
        this.multimodal = DefaultModelCatalog.embeddingSupportsMultimodal(providerKey);
    }

    @Override
    public float[] embedText(String text) {
        try {
            return model.embed(text);
        } catch (RuntimeException e) {
            throw new ProviderException(
                "Spring AI embedding failed (provider=" + providerKey + ")", e);
        }
    }

    @Override
    public Optional<float[]> embedImageAndText(String imageUrl, String textDescription) {
        // Only return a fused vector when the provider genuinely has a shared
        // image+text embedding space (Vertex multimodalembedding). Everyone
        // else: let the worker fall back to embedText(description).
        if (!multimodal) return Optional.empty();
        // The Vertex AI multimodal branch would build a Document with media and
        // call embed(); handled inside a future provider-specific subclass.
        // For the default Spring AI path we decline.
        return Optional.empty();
    }

    @Override
    public int vectorDim() {
        return vectorDim;
    }

    @Override
    public String providerKey() {
        return providerKey;
    }
}
