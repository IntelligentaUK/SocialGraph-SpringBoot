package com.intelligenta.socialgraph.service;

import java.util.Map;
import java.util.Optional;

import com.intelligenta.socialgraph.persistence.SessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private SessionStore sessions;

    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(sessions);
    }

    @Test
    void getSessionGeneratesPublicKeyForNewSessionsOnly() {
        when(sessions.exists("known-session")).thenReturn(false, true);

        Map<String, Object> first = sessionService.getSession("known-session");
        Map<String, Object> second = sessionService.getSession("known-session");

        assertEquals("known-session", first.get("uuid"));
        assertTrue(((Map<?, ?>) first.get("response")).containsKey("pubKey"));
        assertTrue(((Map<?, ?>) second.get("response")).isEmpty());
        verify(sessions).put(eq("known-session"), any());
    }

    @Test
    void getSessionGeneratesUuidWhenRequestIsBlank() {
        when(sessions.exists(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);

        Map<String, Object> response = sessionService.getSession(" ");

        assertTrue(response.containsKey("uuid"));
        assertTrue(((String) response.get("uuid")).length() > 10);
        assertTrue(((Map<?, ?>) response.get("response")).containsKey("pubKey"));
    }
}
