package com.intelligenta.socialgraph.ai.moderation;

import com.intelligenta.socialgraph.ai.ContentModerator;
import com.intelligenta.socialgraph.ai.ProviderException;
import com.intelligenta.socialgraph.model.moderation.ModerationDecision;
import org.springframework.ai.moderation.Categories;
import org.springframework.ai.moderation.CategoryScores;
import org.springframework.ai.moderation.Moderation;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.ai.moderation.ModerationPrompt;
import org.springframework.ai.moderation.ModerationResponse;
import org.springframework.ai.moderation.ModerationResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provider-agnostic {@link ContentModerator} backed by Spring AI's
 * {@link ModerationModel}. Works for OpenAI omni-moderation and
 * Mistral moderation.
 */
public class SpringAiModerator implements ContentModerator {

    private final ModerationModel model;
    private final String providerKey;

    public SpringAiModerator(ModerationModel model, String providerKey) {
        this.model = model;
        this.providerKey = providerKey;
    }

    @Override
    public ModerationDecision moderate(String text) {
        if (text == null || text.isBlank()) return ModerationDecision.allowed();
        try {
            ModerationResponse response = model.call(new ModerationPrompt(text));
            if (response == null) return ModerationDecision.allowed();

            Moderation moderation = response.getResult() == null ? null : response.getResult().getOutput();
            if (moderation == null || moderation.getResults() == null || moderation.getResults().isEmpty()) {
                return ModerationDecision.allowed();
            }

            boolean blocked = false;
            List<String> triggered = new ArrayList<>();
            Map<String, Double> scores = new LinkedHashMap<>();
            for (ModerationResult r : moderation.getResults()) {
                if (r.isFlagged()) blocked = true;
                Categories cats = r.getCategories();
                CategoryScores cs = r.getCategoryScores();
                if (cats != null) {
                    if (cats.isHate()) triggered.add("hate");
                    if (cats.isHateThreatening()) triggered.add("hate_threatening");
                    if (cats.isSelfHarm()) triggered.add("self_harm");
                    if (cats.isSexual()) triggered.add("sexual");
                    if (cats.isSexualMinors()) triggered.add("sexual_minors");
                    if (cats.isViolence()) triggered.add("violence");
                    if (cats.isViolenceGraphic()) triggered.add("violence_graphic");
                    if (cats.isHarassment()) triggered.add("harassment");
                    if (cats.isHarassmentThreatening()) triggered.add("harassment_threatening");
                }
                if (cs != null) {
                    scores.put("hate", cs.getHate());
                    scores.put("self_harm", cs.getSelfHarm());
                    scores.put("sexual", cs.getSexual());
                    scores.put("violence", cs.getViolence());
                    scores.put("harassment", cs.getHarassment());
                }
            }
            return new ModerationDecision(blocked, triggered, scores);
        } catch (RuntimeException e) {
            throw new ProviderException(
                "Spring AI moderation failed (provider=" + providerKey + ")", e);
        }
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public String providerKey() {
        return providerKey;
    }
}
