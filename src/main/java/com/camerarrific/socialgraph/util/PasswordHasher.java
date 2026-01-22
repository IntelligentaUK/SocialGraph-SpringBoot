package com.camerarrific.socialgraph.util;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Utility class for password hashing using Argon2.
 * Argon2 is the winner of the Password Hashing Competition and
 * is considered one of the most secure password hashing algorithms.
 */
@Component
@Slf4j
public class PasswordHasher {

    private static final int ITERATIONS = 3;
    private static final int MEMORY = 65536; // 64 MB
    private static final int PARALLELISM = 4;

    private final Argon2 argon2;

    public PasswordHasher() {
        this.argon2 = Argon2Factory.create();
    }

    /**
     * Create an Argon2 hash of the password with the given salt.
     *
     * @param saltedPassword The salt concatenated with the password
     * @return The Argon2 hash
     */
    public String hash(String saltedPassword) {
        return argon2.hash(ITERATIONS, MEMORY, PARALLELISM, saltedPassword.toCharArray());
    }

    /**
     * Verify a password against a hash.
     *
     * @param saltedPassword The salt concatenated with the password
     * @param hash The hash to verify against
     * @return true if the password matches, false otherwise
     */
    public boolean verify(String saltedPassword, String hash) {
        try {
            return argon2.verify(hash, saltedPassword.toCharArray());
        } catch (Exception e) {
            log.error("Error verifying password hash", e);
            return false;
        }
    }

    /**
     * Generate a cryptographically secure random salt.
     *
     * @return The salt as a string
     */
    public String generateSalt() {
        try {
            SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
            StringBuilder salt = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                salt.append(random.nextInt());
            }
            return salt.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("Error generating salt", e);
            // Fallback to default SecureRandom
            SecureRandom random = new SecureRandom();
            byte[] saltBytes = new byte[24];
            random.nextBytes(saltBytes);
            return bytesToHex(saltBytes);
        }
    }

    /**
     * Generate a unique identifier (UUID-like string).
     *
     * @return A unique identifier
     */
    public String generateUuid() {
        return java.util.UUID.randomUUID().toString();
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}

