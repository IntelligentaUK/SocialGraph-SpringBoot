package com.intelligenta.socialgraph.exception;

/**
 * Exception thrown when trying to follow or unfollow yourself.
 */
public class CannotFollowSelfException extends SocialGraphException {

    public CannotFollowSelfException(String message) {
        super("cannot_follow", message);
    }
}
