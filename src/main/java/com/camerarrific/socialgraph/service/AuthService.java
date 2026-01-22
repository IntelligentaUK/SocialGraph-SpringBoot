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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Service for authentication operations (login, register, token management).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
    private final JwtService jwtService;
    private final PasswordHasher passwordHasher;

    /**
     * Register a new user.
     */
    public AuthResponse register(RegisterRequest request) {
        // Check if username already exists
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ApiException("cannot_register", "Username already registered", HttpStatus.BAD_REQUEST);
        }

        // Generate user identifiers
        String uid = passwordHasher.generateUuid();
        String salt = passwordHasher.generateSalt();
        String poly = passwordHasher.generateUuid();

        // Hash the password with salt
        String passwordHash = passwordHasher.hash(salt + request.getPassword());

        // Create user
        User user = User.builder()
                .uid(uid)
                .username(request.getUsername())
                .email(request.getEmail())
                .fullname(request.getFullName())
                .passwordHash(passwordHash)
                .salt(salt)
                .poly(poly)
                .followers(0L)
                .following(0L)
                .polyCount(1L)
                .activated(false)
                .build();

        // Save user
        userRepository.save(user);

        // Generate JWT token
        String token = jwtService.generateToken(
                user.getUsername(),
                user.getUid(),
                user.getEmail()
        );

        log.info("Registered new user: {} ({})", user.getUsername(), user.getUid());

        return AuthResponse.builder()
                .username(user.getUsername())
                .uid(user.getUid())
                .token(token)
                .expiresIn(jwtService.getExpirationTime() / 1000) // Convert to seconds
                .followers(0L)
                .following(0L)
                .build();
    }

    /**
     * Authenticate a user and generate a JWT token.
     */
    public AuthResponse login(LoginRequest request) {
        // Find user by username
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ApiException(
                        "invalid_grant",
                        "Invalid username or password",
                        HttpStatus.UNAUTHORIZED
                ));

        // Verify password
        String saltedPassword = user.getSalt() + request.getPassword();
        if (!passwordHasher.verify(saltedPassword, user.getPasswordHash())) {
            throw new ApiException(
                    "invalid_grant",
                    "Invalid username or password",
                    HttpStatus.UNAUTHORIZED
            );
        }

        // Increment poly count
        userRepository.incrementField(request.getUsername(), "polyCount", 1);

        // Generate JWT token
        String token = jwtService.generateToken(
                user.getUsername(),
                user.getUid(),
                user.getEmail()
        );

        log.info("User logged in: {} ({})", user.getUsername(), user.getUid());

        return AuthResponse.builder()
                .username(user.getUsername())
                .uid(user.getUid())
                .token(token)
                .expiresIn(jwtService.getExpirationTime() / 1000)
                .followers(user.getFollowers())
                .following(user.getFollowing())
                .build();
    }

    /**
     * Logout a user by blacklisting their token.
     */
    public void logout(String token) {
        // Calculate remaining time for token
        long expirationSeconds = jwtService.getExpirationTime() / 1000;
        tokenRepository.blacklistToken(token, expirationSeconds);
        log.info("Token blacklisted");
    }

    /**
     * Check if a token is blacklisted.
     */
    public boolean isTokenBlacklisted(String token) {
        return tokenRepository.isBlacklisted(token);
    }

    /**
     * Activate a user account.
     */
    public boolean activateAccount(String activationToken) {
        return tokenRepository.getUidByActivationToken(activationToken)
                .flatMap(userRepository::findByUid)
                .map(user -> {
                    userRepository.updateField(user.getUsername(), "activated", "true");
                    tokenRepository.deleteActivationToken(activationToken);
                    log.info("Activated account: {}", user.getUsername());
                    return true;
                })
                .orElse(false);
    }
}

