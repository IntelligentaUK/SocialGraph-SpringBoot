package com.intelligenta.socialgraph.persistence;

import java.util.Map;
import java.util.Optional;

/** RSA session key-pair storage. Short-lived; ephemeral-tier candidate. */
public interface SessionStore {
    boolean exists(String sessionId);
    void put(String sessionId, Map<String, String> keyPair);
    Optional<Map<String, String>> get(String sessionId);
}
