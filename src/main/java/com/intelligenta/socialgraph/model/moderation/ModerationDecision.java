package com.intelligenta.socialgraph.model.moderation;

import java.util.List;
import java.util.Map;

/**
 * Result of a moderation check. {@code blocked=true} causes
 * {@link com.intelligenta.socialgraph.exception.ContentBlockedException} to
 * short-circuit post creation with {@code 400 content_blocked}.
 */
public record ModerationDecision(
    boolean blocked,
    List<String> categories,
    Map<String, Double> scores) {

    public static ModerationDecision allowed() {
        return new ModerationDecision(false, List.of(), Map.of());
    }
}
