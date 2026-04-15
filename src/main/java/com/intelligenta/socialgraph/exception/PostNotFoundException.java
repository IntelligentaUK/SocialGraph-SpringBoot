package com.intelligenta.socialgraph.exception;

/**
 * Exception thrown when a post is not found.
 */
public class PostNotFoundException extends SocialGraphException {

    public PostNotFoundException(String message) {
        super("cannot_perform_action", message);
    }
}
