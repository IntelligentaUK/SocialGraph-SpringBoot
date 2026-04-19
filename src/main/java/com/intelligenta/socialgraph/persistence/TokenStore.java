package com.intelligenta.socialgraph.persistence;

import java.time.Duration;
import java.util.Optional;

/** Bearer-token lookup. Short-lived data; lives in the ephemeral tier under Infinispan. */
public interface TokenStore {
    void issue(String token, String uid, Duration ttl);
    Optional<String> resolve(String token);
    void revoke(String token);
}
