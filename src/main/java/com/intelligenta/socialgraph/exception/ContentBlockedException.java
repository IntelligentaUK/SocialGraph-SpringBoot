package com.intelligenta.socialgraph.exception;

import com.intelligenta.socialgraph.model.moderation.ModerationDecision;

import java.util.List;
import java.util.Map;

/**
 * Thrown by {@link com.intelligenta.socialgraph.service.ShareService#createStatusUpdate}
 * when the active {@link com.intelligenta.socialgraph.ai.ContentModerator}
 * flags the post content. Mapped to {@code 400 content_blocked} by
 * {@link GlobalExceptionHandler}.
 */
public class ContentBlockedException extends RuntimeException {

    private final List<String> categories;
    private final Map<String, Double> scores;

    public ContentBlockedException(ModerationDecision decision) {
        super("post content blocked: " + String.join(",", decision.categories()));
        this.categories = decision.categories();
        this.scores = decision.scores();
    }

    public List<String> getCategories() {
        return categories;
    }

    public Map<String, Double> getScores() {
        return scores;
    }
}
