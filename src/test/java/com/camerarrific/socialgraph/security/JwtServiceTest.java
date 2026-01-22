package com.camerarrific.socialgraph.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for JwtService.
 */
class JwtServiceTest {

    private JwtService jwtService;

    private static final String TEST_SECRET = "test-secret-key-minimum-32-characters-for-testing";
    private static final long EXPIRATION = 3600000L; // 1 hour
    private static final String ISSUER = "socialgraph-test";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", EXPIRATION);
        ReflectionTestUtils.setField(jwtService, "issuer", ISSUER);
    }

    @Test
    @DisplayName("Should generate a valid JWT token")
    void shouldGenerateValidToken() {
        // Given
        String username = "testuser";
        String uid = "test-uid-123";
        String email = "test@example.com";

        // When
        String token = jwtService.generateToken(username, uid, email);

        // Then
        assertThat(token).isNotNull().isNotEmpty();
        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    @DisplayName("Should extract username from token")
    void shouldExtractUsername() {
        // Given
        String username = "testuser";
        String token = jwtService.generateToken(username, "uid", "email@test.com");

        // When
        String extractedUsername = jwtService.extractUsername(token);

        // Then
        assertThat(extractedUsername).isEqualTo(username);
    }

    @Test
    @DisplayName("Should extract UID from token")
    void shouldExtractUid() {
        // Given
        String uid = "test-uid-456";
        String token = jwtService.generateToken("user", uid, "email@test.com");

        // When
        String extractedUid = jwtService.extractUid(token);

        // Then
        assertThat(extractedUid).isEqualTo(uid);
    }

    @Test
    @DisplayName("Should validate token for correct user")
    void shouldValidateTokenForCorrectUser() {
        // Given
        String username = "testuser";
        String uid = "test-uid";
        String token = jwtService.generateToken(username, uid, "email@test.com");
        
        UserPrincipal principal = UserPrincipal.builder()
                .uid(uid)
                .username(username)
                .email("email@test.com")
                .enabled(true)
                .build();

        // When
        boolean isValid = jwtService.isTokenValid(token, principal);

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Should reject token for wrong user")
    void shouldRejectTokenForWrongUser() {
        // Given
        String token = jwtService.generateToken("user1", "uid1", "email@test.com");
        
        UserPrincipal wrongPrincipal = UserPrincipal.builder()
                .uid("uid2")
                .username("user2")
                .email("other@test.com")
                .enabled(true)
                .build();

        // When
        boolean isValid = jwtService.isTokenValid(token, wrongPrincipal);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should parse token and return claims")
    void shouldParseTokenAndReturnClaims() {
        // Given
        String username = "testuser";
        String uid = "test-uid";
        String email = "test@example.com";
        String token = jwtService.generateToken(username, uid, email);

        // When
        Optional<Claims> claimsOpt = jwtService.parseToken(token);

        // Then
        assertThat(claimsOpt).isPresent();
        Claims claims = claimsOpt.get();
        assertThat(claims.getSubject()).isEqualTo(username);
        assertThat(claims.get("uid", String.class)).isEqualTo(uid);
        assertThat(claims.get("email", String.class)).isEqualTo(email);
    }

    @Test
    @DisplayName("Should return empty for invalid token")
    void shouldReturnEmptyForInvalidToken() {
        // When
        Optional<Claims> claimsOpt = jwtService.parseToken("invalid-token");

        // Then
        assertThat(claimsOpt).isEmpty();
    }

    @Test
    @DisplayName("Should return correct expiration time")
    void shouldReturnCorrectExpirationTime() {
        // When
        long expiration = jwtService.getExpirationTime();

        // Then
        assertThat(expiration).isEqualTo(EXPIRATION);
    }
}

