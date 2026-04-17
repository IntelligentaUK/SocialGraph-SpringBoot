package com.intelligenta.socialgraph.ai.embedding;

import com.intelligenta.socialgraph.ai.EmbeddingProvider;
import com.intelligenta.socialgraph.config.EmbeddingProperties;
import com.intelligenta.socialgraph.service.EmbeddingClient;

import java.util.Optional;

/**
 * {@link EmbeddingProvider} backed by the Rust embedding sidecar
 * (SigLIP-2 giant). This is the default — zero external API keys needed —
 * and remains selectable via {@code ai.embedding.provider=sidecar}.
 *
 * <p>Unlike every Spring AI-backed provider, the sidecar exposes a genuine
 * image+text shared embedding space, so {@link #embedImageAndText} returns
 * a populated {@link Optional}.
 */
public class SidecarEmbeddingProvider implements EmbeddingProvider {

    public static final String PROVIDER_KEY = "sidecar";

    private final EmbeddingClient client;
    private final int vectorDim;

    public SidecarEmbeddingProvider(EmbeddingClient client, EmbeddingProperties props) {
        this.client = client;
        this.vectorDim = props.getVectorDim();
    }

    @Override
    public float[] embedText(String text) {
        return client.embedText(text);
    }

    @Override
    public Optional<float[]> embedImageAndText(String imageUrl, String textDescription) {
        return Optional.of(client.embedImageAndText(imageUrl, textDescription));
    }

    @Override
    public int vectorDim() {
        return vectorDim;
    }

    @Override
    public String providerKey() {
        return PROVIDER_KEY;
    }
}
