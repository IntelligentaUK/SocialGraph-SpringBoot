package com.intelligenta.socialgraph.exception;

/**
 * Exception thrown when login credentials are invalid.
 */
public class InvalidCredentialsException extends SocialGraphException {

    public InvalidCredentialsException(String message) {
        super("invalid_grant", message);
    }
}
