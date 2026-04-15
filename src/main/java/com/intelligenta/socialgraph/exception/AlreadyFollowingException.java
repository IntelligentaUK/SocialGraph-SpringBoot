package com.intelligenta.socialgraph.exception;

/**
 * Exception thrown when trying to follow a user already being followed.
 */
public class AlreadyFollowingException extends SocialGraphException {

    public AlreadyFollowingException(String message) {
        super("cannot_follow", message);
    }
}
