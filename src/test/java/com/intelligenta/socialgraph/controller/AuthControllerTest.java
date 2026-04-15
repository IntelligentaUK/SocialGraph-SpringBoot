package com.intelligenta.socialgraph.controller;

import com.intelligenta.socialgraph.model.AuthResponse;
import com.intelligenta.socialgraph.service.SessionService;
import com.intelligenta.socialgraph.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private SessionService sessionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(userService, sessionService)).build();
    }

    @Test
    void loginDelegatesToUserService() throws Exception {
        when(userService.login("alice", "secret")).thenReturn(new AuthResponse("alice", "token-1", "uid-1", 120));

        mockMvc.perform(post("/api/login")
                .param("username", "alice")
                .param("password", "secret"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.token").value("token-1"))
            .andExpect(jsonPath("$.expires_in").value(120));
    }

    @Test
    void registerDelegatesToUserService() throws Exception {
        when(userService.register("alice", "secret", "alice@example.com"))
            .thenReturn(new AuthResponse("alice", "token-2", "uid-2", 3600));

        mockMvc.perform(post("/api/register")
                .param("username", "alice")
                .param("password", "secret")
                .param("email", "alice@example.com"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uid").value("uid-2"));
    }

    @Test
    void activateReturnsBadRequestWhenTokenIsUnknown() throws Exception {
        when(userService.activateAccount("missing")).thenReturn(Map.of("activated", "false"));

        mockMvc.perform(get("/api/activate").param("token", "missing"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.activated").value("false"));
    }

    @Test
    void sessionAndPingExposePublicEndpoints() throws Exception {
        when(sessionService.getSession("requested-uuid"))
            .thenReturn(Map.of("uuid", "requested-uuid", "response", Map.of("pubKey", "pub-key")));

        mockMvc.perform(get("/api/session").param("uuid", "requested-uuid"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uuid").value("requested-uuid"))
            .andExpect(jsonPath("$.response.pubKey").value("pub-key"));

        mockMvc.perform(get("/api/ping"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").value("hello"));

        verify(sessionService).getSession("requested-uuid");
    }
}
