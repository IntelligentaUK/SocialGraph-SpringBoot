package com.intelligenta.socialgraph.ai;

/**
 * Summarizes the video content of a post for retrieval. The summary is
 * SigLIP-2-tokenizer-friendly (≤256 tokens) and is concatenated with the
 * caption text before being fed to the text embedding.
 */
public interface VideoSummarizer {

    /**
     * @param statusText user-provided caption; may be blank
     * @param mediaUrl   URL of the video artifact; must be non-blank
     * @return a summary string (never {@code null}; may be empty for empty inputs)
     */
    String summarize(String statusText, String mediaUrl);
}
