package com.camerarrific.socialgraph.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Enum representing social actions that can be performed on posts.
 */
@Getter
@RequiredArgsConstructor
public enum Action {

    LIKE(":likes", "like", "likes", "liked"),
    LOVE(":loves", "love", "loves", "loved"),
    FAV(":favs", "fav", "faves", "faved"),
    SHARE(":shares", "share", "shares", "shared");

    private final String key;
    private final String noun;
    private final String plural;
    private final String pastTense;

    /**
     * Get the Redis key suffix for this action.
     */
    public String getKey() {
        return key;
    }

    /**
     * Get the noun form (like, love, fav, share).
     */
    public String getNoun() {
        return noun;
    }

    /**
     * Get the plural form (likes, loves, faves, shares).
     */
    public String getPlural() {
        return plural;
    }

    /**
     * Get the past tense form (liked, loved, faved, shared).
     */
    public String getPastTense() {
        return pastTense;
    }

    /**
     * Parse action from string.
     */
    public static Action fromString(String value) {
        for (Action action : Action.values()) {
            if (action.noun.equalsIgnoreCase(value) || 
                action.plural.equalsIgnoreCase(value) ||
                action.name().equalsIgnoreCase(value)) {
                return action;
            }
        }
        throw new IllegalArgumentException("Unknown action: " + value);
    }
}

