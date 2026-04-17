package com.intelligenta.socialgraph.ai;

import java.util.List;

/**
 * Produces a concise retrieval-oriented summary from a caption plus up to
 * a few image URLs. The summary is SigLIP-2-tokenizer-friendly (≤256 tokens)
 * and is used as the text half of the {@code combined_vec} embedding.
 */
public interface VisualSummarizer {

    /**
     * @param statusText the user-provided caption; may be blank
     * @param imageUrls  up to {@code embedding.images-for-embedding} image URLs;
     *                   ignored if {@link #supportsVision()} is false
     * @return a summary string (never {@code null}; may be empty for empty inputs)
     */
    String summarize(String statusText, List<String> imageUrls);

    /**
     * Whether the active chat provider can actually see image inputs. When
     * false the summary degrades to a caption-only paraphrase.
     */
    boolean supportsVision();
}
