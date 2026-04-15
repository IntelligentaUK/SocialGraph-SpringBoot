package com.intelligenta.socialgraph.exception;

/**
 * Exception thrown when an authentication token is invalid.
 */
public class InvalidTokenException extends SocialGraphException {

    public InvalidTokenException(String message) {
        super("auth_error", message);
    }
}
