package com.intelligenta.socialgraph.ai.chat;

import com.intelligenta.socialgraph.ai.VisualSummarizer;

import java.util.List;

/**
 * Placeholder used when {@code ai.chat.provider=none}. Returns the input
 * status text verbatim so the embedding pipeline can still produce a
 * text-only vector without requiring a chat model.
 */
public class NoopSummarizer implements VisualSummarizer {

    @Override
    public String summarize(String statusText, List<String> imageUrls) {
        return statusText == null ? "" : statusText;
    }

    @Override
    public boolean supportsVision() {
        return false;
    }
}
