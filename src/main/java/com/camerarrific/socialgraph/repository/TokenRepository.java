package com.camerarrific.socialgraph.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Repository for token-related Redis operations.
 * Note: With JWT, this is primarily for token blacklisting/revocation.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class TokenRepository {

    private static final String TOKENS_PREFIX = "tokens:";
    private static final String BLACKLIST_PREFIX = "tokens:blacklist:";
    private static final String ACTIVATION_PREFIX = "user:activations:";

    private final StringRedisTemplate redisTemplate;

    /**
     * Store a token with expiration.
     * Legacy support for session tokens.
     */
    public void saveToken(String token, String uid, long expirationSeconds) {
        String key = TOKENS_PREFIX + token;
        redisTemplate.opsForValue().set(key, uid, expirationSeconds, TimeUnit.SECONDS);
        log.debug("Saved token for user {}, expires in {} seconds", uid, expirationSeconds);
    }

    /**
     * Get the user ID associated with a token.
     * Legacy support for session tokens.
     */
    public Optional<String> getUidByToken(String token) {
        String key = TOKENS_PREFIX + token;
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    /**
     * Delete a token.
     */
    public Boolean deleteToken(String token) {
        String key = TOKENS_PREFIX + token;
        return redisTemplate.delete(key);
    }

    /**
     * Blacklist a JWT token (for logout/revocation).
     */
    public void blacklistToken(String token, long expirationSeconds) {
        String key = BLACKLIST_PREFIX + token;
        redisTemplate.opsForValue().set(key, "1", expirationSeconds, TimeUnit.SECONDS);
        log.debug("Blacklisted token, expires in {} seconds", expirationSeconds);
    }

    /**
     * Check if a token is blacklisted.
     */
    public boolean isBlacklisted(String token) {
        String key = BLACKLIST_PREFIX + token;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Save an activation token.
     */
    public void saveActivationToken(String activationToken, String uid, long expirationSeconds) {
        redisTemplate.opsForValue().set(
            ACTIVATION_PREFIX + activationToken + ":uid", 
            uid, 
            expirationSeconds, 
            TimeUnit.SECONDS
        );
    }

    /**
     * Get UID by activation token.
     */
    public Optional<String> getUidByActivationToken(String activationToken) {
        String key = ACTIVATION_PREFIX + activationToken + ":uid";
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    /**
     * Delete an activation token.
     */
    public Boolean deleteActivationToken(String activationToken) {
        String key = ACTIVATION_PREFIX + activationToken + ":uid";
        return redisTemplate.delete(key);
    }
}

