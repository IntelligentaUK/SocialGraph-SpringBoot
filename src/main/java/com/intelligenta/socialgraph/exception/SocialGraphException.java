package com.intelligenta.socialgraph.exception;

/**
 * Base exception for Social Graph application.
 */
public class SocialGraphException extends RuntimeException {

    private final String errorCode;

    public SocialGraphException(String message) {
        super(message);
        this.errorCode = "error";
    }

    public SocialGraphException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
