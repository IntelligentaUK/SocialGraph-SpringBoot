package com.intelligenta.socialgraph.service;

import com.intelligenta.socialgraph.exception.SocialGraphException;
import com.intelligenta.socialgraph.util.Util;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal session key exchange service backed by Redis.
 */
@Service
public class SessionService {

    private final StringRedisTemplate redisTemplate;

    public SessionService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Map<String, Object> getSession(String requestedUuid) {
        String uuid = requestedUuid == null || requestedUuid.isBlank() ? Util.UUID() : requestedUuid;
        String key = "session:" + uuid;

        Map<String, Object> response = new HashMap<>();
        Map<String, Object> serverResponse = new HashMap<>();

        if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            Map<String, String> sessionKeys = generateSessionKeys();
            redisTemplate.opsForHash().putAll(key, sessionKeys);
            redisTemplate.expire(key, Duration.ofDays(1));
            serverResponse.put("pubKey", sessionKeys.get("publicKey"));
        }

        response.put("uuid", uuid);
        response.put("response", serverResponse);
        return response;
    }

    private Map<String, String> generateSessionKeys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();

            Map<String, String> keys = new HashMap<>();
            keys.put("publicKey", Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
            keys.put("privateKey", Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
            return keys;
        } catch (NoSuchAlgorithmException e) {
            throw new SocialGraphException("session_key_failure", "Unable to create a session key pair");
        }
    }
}
