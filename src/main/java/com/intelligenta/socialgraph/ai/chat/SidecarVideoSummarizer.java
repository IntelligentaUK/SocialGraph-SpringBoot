package com.intelligenta.socialgraph.ai.chat;

import com.intelligenta.socialgraph.ai.VideoSummarizer;
import com.intelligenta.socialgraph.service.EmbeddingClient;

/**
 * {@link VideoSummarizer} backed by the Rust sidecar's
 * {@code /summarize/video} endpoint, which internally runs the Gemma-EV VLM.
 */
public class SidecarVideoSummarizer implements VideoSummarizer {

    public static final String PROVIDER_KEY = "sidecar";

    private final EmbeddingClient client;

    public SidecarVideoSummarizer(EmbeddingClient client) {
        this.client = client;
    }

    @Override
    public String summarize(String statusText, String mediaUrl) {
        return client.summarizeVideo(statusText, mediaUrl);
    }
}
