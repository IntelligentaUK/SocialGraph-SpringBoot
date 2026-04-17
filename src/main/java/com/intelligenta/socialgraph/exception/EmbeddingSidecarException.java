package com.intelligenta.socialgraph.exception;

/**
 * Raised by {@code EmbeddingClient} when the sidecar is unreachable, returns
 * a non-2xx response, or returns a malformed body. The embedding worker
 * catches this to apply the retry/DLQ policy.
 */
public class EmbeddingSidecarException extends RuntimeException {

    public EmbeddingSidecarException(String message) {
        super(message);
    }

    public EmbeddingSidecarException(String message, Throwable cause) {
        super(message, cause);
    }
}
