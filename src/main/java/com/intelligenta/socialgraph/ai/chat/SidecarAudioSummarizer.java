package com.intelligenta.socialgraph.ai.chat;

import com.intelligenta.socialgraph.ai.AudioSummarizer;
import com.intelligenta.socialgraph.service.EmbeddingClient;

/**
 * {@link AudioSummarizer} backed by the Rust sidecar's
 * {@code /summarize/audio} endpoint, which internally runs the Gemma-EV VLM.
 */
public class SidecarAudioSummarizer implements AudioSummarizer {

    public static final String PROVIDER_KEY = "sidecar";

    private final EmbeddingClient client;

    public SidecarAudioSummarizer(EmbeddingClient client) {
        this.client = client;
    }

    @Override
    public String summarize(String statusText, String mediaUrl) {
        return client.summarizeAudio(statusText, mediaUrl);
    }
}
