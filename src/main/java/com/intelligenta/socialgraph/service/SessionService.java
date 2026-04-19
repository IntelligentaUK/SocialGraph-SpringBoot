package com.intelligenta.socialgraph.service;

import com.intelligenta.socialgraph.exception.SocialGraphException;
import com.intelligenta.socialgraph.persistence.SessionStore;
import com.intelligenta.socialgraph.util.Util;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** Minimal session key-exchange service backed by a {@link SessionStore}. */
@Service
public class SessionService {

    private final SessionStore sessions;

    public SessionService(SessionStore sessions) {
        this.sessions = sessions;
    }

    public Map<String, Object> getSession(String requestedUuid) {
        String uuid = requestedUuid == null || requestedUuid.isBlank() ? Util.UUID() : requestedUuid;

        Map<String, Object> response = new HashMap<>();
        Map<String, Object> serverResponse = new HashMap<>();

        if (!sessions.exists(uuid)) {
            Map<String, String> sessionKeys = generateSessionKeys();
            sessions.put(uuid, sessionKeys);
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
