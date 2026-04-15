package com.intelligenta.socialgraph.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        sessionService = new SessionService(redisTemplate);
    }

    @Test
    void getSessionGeneratesPublicKeyForNewSessionsOnly() {
        when(redisTemplate.hasKey("session:known-session")).thenReturn(false, true);

        Map<String, Object> firstResponse = sessionService.getSession("known-session");
        Map<String, Object> secondResponse = sessionService.getSession("known-session");

        assertEquals("known-session", firstResponse.get("uuid"));
        assertTrue(((Map<?, ?>) firstResponse.get("response")).containsKey("pubKey"));
        assertTrue(((Map<?, ?>) secondResponse.get("response")).isEmpty());
        verify(hashOperations).putAll(eq("session:known-session"), any(Map.class));
        verify(redisTemplate).expire("session:known-session", Duration.ofDays(1));
    }

    @Test
    void getSessionGeneratesUuidWhenRequestIsBlank() {
        when(redisTemplate.hasKey(org.mockito.ArgumentMatchers.startsWith("session:"))).thenReturn(false);

        Map<String, Object> response = sessionService.getSession(" ");

        assertTrue(response.containsKey("uuid"));
        assertTrue(((String) response.get("uuid")).length() > 10);
        assertTrue(((Map<?, ?>) response.get("response")).containsKey("pubKey"));
    }
}
