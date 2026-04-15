package com.intelligenta.socialgraph.exception;

/**
 * Exception thrown when trying to register a username that already exists.
 */
public class AlreadyRegisteredException extends SocialGraphException {

    public AlreadyRegisteredException(String message) {
        super("cannot_register", message);
    }
}
