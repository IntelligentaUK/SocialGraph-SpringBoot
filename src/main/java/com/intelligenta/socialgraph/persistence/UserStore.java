package com.intelligenta.socialgraph.persistence;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * User profile persistence. Redis stores this as a hash keyed by username plus
 * a {@code user:uid} reverse-lookup hash. Infinispan uses transactional caches
 * to make registration (the 5-key atomic mutation) consistent across nodes.
 */
public interface UserStore {

    /** Atomic register. Must fail if the username is taken. Returns activation token. */
    String register(String username, Map<String, String> userHash, String uid,
                    String token, Duration tokenTtl);

    Optional<Map<String, String>> find(String username);
    Optional<String> findUsernameByUid(String uid);
    Optional<String> findUidByUsername(String username);
    boolean exists(String username);
    boolean uidExists(String uid);

    Optional<String> getField(String username, String field);
    List<Optional<String>> getFields(String username, List<String> fields);
    void putField(String username, String field, String value);
    void putAll(String username, Map<String, String> fields);
    void incrementField(String username, String field, long delta);

    /** Admin listing for {@code /api/users/search}. Returns (uid, username) pairs. */
    Map<String, String> allUidToUsername();

    Optional<String> getPublicRsaKey(String uid);

    Optional<String> consumeActivationToken(String activationToken);
}
