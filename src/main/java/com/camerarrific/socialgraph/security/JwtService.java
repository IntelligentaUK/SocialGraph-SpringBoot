package com.camerarrific.socialgraph.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Service for JWT token generation and validation.
 * Uses HS256 algorithm for signing tokens.
 */
@Service
@Slf4j
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${jwt.issuer}")
    private String issuer;

    /**
     * Extract username from JWT token.
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extract user ID from JWT token.
     */
    public String extractUid(String token) {
        return extractClaim(token, claims -> claims.get("uid", String.class));
    }

    /**
     * Extract a specific claim from the token.
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Generate a JWT token for a user.
     */
    public String generateToken(String username, String uid, String email) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("uid", uid);
        extraClaims.put("email", email);
        return generateToken(extraClaims, username);
    }

    /**
     * Generate a JWT token with extra claims.
     */
    public String generateToken(Map<String, Object> extraClaims, String username) {
        return buildToken(extraClaims, username, jwtExpiration);
    }

    /**
     * Get the token expiration time in milliseconds.
     */
    public long getExpirationTime() {
        return jwtExpiration;
    }

    private String buildToken(Map<String, Object> extraClaims, String subject, long expiration) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuer(issuer)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Validate if the token is valid for the given user.
     */
    public boolean isTokenValid(String token, UserPrincipal userPrincipal) {
        try {
            final String username = extractUsername(token);
            return (username.equals(userPrincipal.getUsername())) && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validate if the token is structurally valid and not expired.
     */
    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return !isTokenExpired(token);
        } catch (ExpiredJwtException e) {
            log.debug("JWT token is expired: {}", e.getMessage());
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Parse token and return claims if valid.
     */
    public Optional<Claims> parseToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            if (!isTokenExpired(token)) {
                return Optional.of(claims);
            }
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Failed to parse JWT token: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        // If the secret is base64 encoded, decode it; otherwise use it directly
        byte[] keyBytes;
        if (secretKey.length() >= 32) {
            keyBytes = secretKey.getBytes();
        } else {
            keyBytes = Decoders.BASE64.decode(secretKey);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}

