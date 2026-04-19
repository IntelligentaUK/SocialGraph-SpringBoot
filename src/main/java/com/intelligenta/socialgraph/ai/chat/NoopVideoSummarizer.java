package com.intelligenta.socialgraph.ai.chat;

import com.intelligenta.socialgraph.ai.VideoSummarizer;

/**
 * Placeholder used when {@code ai.video.provider=none}. Returns the input
 * caption verbatim so the embedding pipeline can still produce a text-only
 * vector without requiring a video model.
 */
public class NoopVideoSummarizer implements VideoSummarizer {

    @Override
    public String summarize(String statusText, String mediaUrl) {
        return statusText == null ? "" : statusText;
    }
}
