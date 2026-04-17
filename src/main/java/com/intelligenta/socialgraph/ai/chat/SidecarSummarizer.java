package com.intelligenta.socialgraph.ai.chat;

import com.intelligenta.socialgraph.ai.VisualSummarizer;
import com.intelligenta.socialgraph.service.EmbeddingClient;

import java.util.List;

/**
 * {@link VisualSummarizer} backed by the Rust sidecar's {@code /summarize}
 * endpoint, which internally runs the Gemma VLM. Fully supports vision.
 */
public class SidecarSummarizer implements VisualSummarizer {

    public static final String PROVIDER_KEY = "sidecar";

    private final EmbeddingClient client;

    public SidecarSummarizer(EmbeddingClient client) {
        this.client = client;
    }

    @Override
    public String summarize(String statusText, List<String> imageUrls) {
        return client.summarize(statusText, imageUrls);
    }

    @Override
    public boolean supportsVision() {
        return true;
    }
}
