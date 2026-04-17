package com.intelligenta.socialgraph.ai;

/**
 * Uniform wrapper for failures inside an AI provider adapter — HTTP errors
 * from a remote model provider, Spring AI client exceptions, or any
 * unexpected response shape. {@link com.intelligenta.socialgraph.exception.GlobalExceptionHandler}
 * maps this to {@code 502 Bad Gateway}.
 */
public class ProviderException extends RuntimeException {

    public ProviderException(String message) {
        super(message);
    }

    public ProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
