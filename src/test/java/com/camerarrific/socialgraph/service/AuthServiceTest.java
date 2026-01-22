package com.camerarrific.socialgraph.service;

import com.camerarrific.socialgraph.dto.request.LoginRequest;
import com.camerarrific.socialgraph.dto.request.RegisterRequest;
import com.camerarrific.socialgraph.dto.response.AuthResponse;
import com.camerarrific.socialgraph.exception.ApiException;
import com.camerarrific.socialgraph.model.User;
import com.camerarrific.socialgraph.repository.TokenRepository;
import com.camerarrific.socialgraph.repository.UserRepository;
import com.camerarrific.socialgraph.security.JwtService;
import com.camerarrific.socialgraph.util.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenRepository tokenRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordHasher passwordHasher;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        registerRequest = RegisterRequest.builder()
                .username("testuser")
                .password("password123")
                .email("test@example.com")
                .fullName("Test User")
                .build();

        loginRequest = LoginRequest.builder()
                .username("testuser")
                .password("password123")
                .build();
    }

    @Test
    @DisplayName("Should register new user successfully")
    void shouldRegisterNewUser() {
        // Given
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(passwordHasher.generateUuid()).thenReturn("test-uid");
        when(passwordHasher.generateSalt()).thenReturn("test-salt");
        when(passwordHasher.hash(anyString())).thenReturn("hashed-password");
        when(jwtService.generateToken(anyString(), anyString(), anyString())).thenReturn("jwt-token");
        when(jwtService.getExpirationTime()).thenReturn(86400000L);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        // When
        AuthResponse response = authService.register(registerRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getUsername()).isEqualTo("testuser");
        assertThat(response.getUid()).isEqualTo("test-uid");
        assertThat(response.getToken()).isEqualTo("jwt-token");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should reject registration if username exists")
    void shouldRejectDuplicateUsername() {
        // Given
        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("error", "cannot_register");
    }

    @Test
    @DisplayName("Should login user with correct credentials")
    void shouldLoginWithCorrectCredentials() {
        // Given
        User user = User.builder()
                .uid("user-uid")
                .username("testuser")
                .email("test@example.com")
                .passwordHash("hashed-password")
                .salt("salt")
                .followers(10L)
                .following(5L)
                .build();

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordHasher.verify("saltpassword123", "hashed-password")).thenReturn(true);
        when(jwtService.generateToken(anyString(), anyString(), anyString())).thenReturn("jwt-token");
        when(jwtService.getExpirationTime()).thenReturn(86400000L);

        // When
        AuthResponse response = authService.login(loginRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getUsername()).isEqualTo("testuser");
        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getFollowers()).isEqualTo(10L);
        assertThat(response.getFollowing()).isEqualTo(5L);
    }

    @Test
    @DisplayName("Should reject login with wrong password")
    void shouldRejectWrongPassword() {
        // Given
        User user = User.builder()
                .uid("user-uid")
                .username("testuser")
                .passwordHash("hashed-password")
                .salt("salt")
                .build();

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordHasher.verify(anyString(), anyString())).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("error", "invalid_grant");
    }

    @Test
    @DisplayName("Should reject login for non-existent user")
    void shouldRejectNonExistentUser() {
        // Given
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("error", "invalid_grant");
    }

    @Test
    @DisplayName("Should blacklist token on logout")
    void shouldBlacklistTokenOnLogout() {
        // Given
        when(jwtService.getExpirationTime()).thenReturn(86400000L);

        // When
        authService.logout("test-token");

        // Then
        verify(tokenRepository).blacklistToken("test-token", 86400L);
    }
}

