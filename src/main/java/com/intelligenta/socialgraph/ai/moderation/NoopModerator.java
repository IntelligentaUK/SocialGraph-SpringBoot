package com.intelligenta.socialgraph.ai.moderation;

import com.intelligenta.socialgraph.ai.ContentModerator;
import com.intelligenta.socialgraph.model.moderation.ModerationDecision;

/**
 * Default when {@code ai.moderation.provider=none}. Every check allows the
 * content through — matches behaviour before moderation was added so that
 * existing installs see no change until they opt in.
 */
public class NoopModerator implements ContentModerator {

    @Override
    public ModerationDecision moderate(String text) {
        return ModerationDecision.allowed();
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public String providerKey() {
        return "none";
    }
}
