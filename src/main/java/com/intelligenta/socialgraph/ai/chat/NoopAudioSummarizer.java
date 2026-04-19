package com.intelligenta.socialgraph.ai.chat;

import com.intelligenta.socialgraph.ai.AudioSummarizer;

/**
 * Placeholder used when {@code ai.audio.provider=none}. Returns the input
 * caption verbatim so the embedding pipeline can still produce a text-only
 * vector without requiring an audio model.
 */
public class NoopAudioSummarizer implements AudioSummarizer {

    @Override
    public String summarize(String statusText, String mediaUrl) {
        return statusText == null ? "" : statusText;
    }
}
