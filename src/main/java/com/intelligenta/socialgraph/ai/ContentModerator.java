package com.intelligenta.socialgraph.ai;

import com.intelligenta.socialgraph.model.moderation.ModerationDecision;

/**
 * Checks post content against a provider's moderation API (OpenAI omni-mod,
 * Mistral moderation, etc.) before the post is persisted. When
 * {@link #enabled()} is false (the default) all calls return a permissive
 * decision.
 */
public interface ContentModerator {

    ModerationDecision moderate(String text);

    /** False when {@code ai.moderation.provider=none}. */
    boolean enabled();

    String providerKey();
}
