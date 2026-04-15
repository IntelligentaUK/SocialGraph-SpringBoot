package com.intelligenta.socialgraph.exception;

/**
 * Exception thrown when trying to unfollow a user not being followed.
 */
public class NotFollowingException extends SocialGraphException {

    public NotFollowingException(String message) {
        super("cannot_unfollow", message);
    }
}
