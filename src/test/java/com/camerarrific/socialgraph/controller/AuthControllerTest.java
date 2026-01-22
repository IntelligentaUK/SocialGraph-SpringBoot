package com.camerarrific.socialgraph.controller;

import com.camerarrific.socialgraph.dto.request.LoginRequest;
import com.camerarrific.socialgraph.dto.request.RegisterRequest;
import com.camerarrific.socialgraph.dto.response.AuthResponse;
import com.camerarrific.socialgraph.service.AuthService;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for AuthController.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class AuthControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("POST /api/auth/login - should return auth response on valid login")
    void shouldLoginSuccessfully() throws Exception {
        // Given
        LoginRequest request = LoginRequest.builder()
                .username("testuser")
                .password("password123")
                .build();

        AuthResponse response = AuthResponse.builder()
                .username("testuser")
                .uid("test-uid")
                .token("jwt-token")
                .expiresIn(86400L)
                .followers(10L)
                .following(5L)
                .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        // When/Then
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.uid").value("test-uid"))
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    @Test
    @DisplayName("POST /api/auth/register - should return auth response on valid registration")
    void shouldRegisterSuccessfully() throws Exception {
        // Given
        RegisterRequest request = RegisterRequest.builder()
                .username("newuser")
                .password("password123")
                .email("new@example.com")
                .build();

        AuthResponse response = AuthResponse.builder()
                .username("newuser")
                .uid("new-uid")
                .token("jwt-token")
                .expiresIn(86400L)
                .followers(0L)
                .following(0L)
                .build();

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        // When/Then
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("newuser"))
                .andExpect(jsonPath("$.uid").value("new-uid"));
    }

    @Test
    @DisplayName("POST /api/auth/login - should return 400 on invalid request")
    void shouldReturn400OnInvalidLogin() throws Exception {
        // Given - missing password
        LoginRequest request = LoginRequest.builder()
                .username("testuser")
                .build();

        // When/Then
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/register - should return 400 on invalid email")
    void shouldReturn400OnInvalidEmail() throws Exception {
        // Given
        RegisterRequest request = RegisterRequest.builder()
                .username("newuser")
                .password("password123")
                .email("invalid-email")
                .build();

        // When/Then
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}

