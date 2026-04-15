package com.intelligenta.socialgraph.exception;

/**
 * Exception thrown when a user is not found.
 */
public class UserNotFoundException extends SocialGraphException {

    public UserNotFoundException(String message) {
        super("user_not_found", message);
    }
}
