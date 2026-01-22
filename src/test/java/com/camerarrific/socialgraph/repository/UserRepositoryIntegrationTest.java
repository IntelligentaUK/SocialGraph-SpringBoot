package com.camerarrific.socialgraph.repository;

import com.camerarrific.socialgraph.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for UserRepository using Testcontainers.
 */
@SpringBootTest
@ActiveProfiles("test")
class UserRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        // Clean up Redis before each test
        Set<String> keys = redisTemplate.keys("user:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("Should save and retrieve user")
    void shouldSaveAndRetrieveUser() {
        // Given
        User user = User.builder()
                .uid("test-uid-123")
                .username("testuser")
                .email("test@example.com")
                .fullname("Test User")
                .passwordHash("hashed")
                .salt("salt")
                .poly("poly")
                .followers(0L)
                .following(0L)
                .build();

        // When
        userRepository.save(user);
        Optional<User> retrieved = userRepository.findByUsername("testuser");

        // Then
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getUid()).isEqualTo("test-uid-123");
        assertThat(retrieved.get().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("Should check user existence")
    void shouldCheckUserExistence() {
        // Given
        User user = User.builder()
                .uid("test-uid")
                .username("existinguser")
                .email("existing@example.com")
                .passwordHash("hash")
                .salt("salt")
                .build();
        userRepository.save(user);

        // When/Then
        assertThat(userRepository.existsByUsername("existinguser")).isTrue();
        assertThat(userRepository.existsByUsername("nonexistent")).isFalse();
    }

    @Test
    @DisplayName("Should find username by UID")
    void shouldFindUsernameByUid() {
        // Given
        User user = User.builder()
                .uid("uid-456")
                .username("findableuser")
                .email("find@example.com")
                .passwordHash("hash")
                .salt("salt")
                .build();
        userRepository.save(user);

        // When
        Optional<String> username = userRepository.findUsernameByUid("uid-456");

        // Then
        assertThat(username).isPresent();
        assertThat(username.get()).isEqualTo("findableuser");
    }

    @Test
    @DisplayName("Should manage set membership (followers)")
    void shouldManageSetMembership() {
        // Given
        String userId = "user-1";
        String followerId = "follower-1";

        // When
        userRepository.addToSet(userId, "followers", followerId);

        // Then
        assertThat(userRepository.isMemberOfSet(userId, "followers", followerId)).isTrue();
        assertThat(userRepository.isMemberOfSet(userId, "followers", "other")).isFalse();
        
        Set<String> followers = userRepository.getSetMembers(userId, "followers");
        assertThat(followers).contains(followerId);
    }

    @Test
    @DisplayName("Should increment field")
    void shouldIncrementField() {
        // Given
        User user = User.builder()
                .uid("uid-inc")
                .username("incuser")
                .email("inc@example.com")
                .passwordHash("hash")
                .salt("salt")
                .followers(0L)
                .build();
        userRepository.save(user);

        // When
        Long newValue = userRepository.incrementField("incuser", "followers", 1);

        // Then
        assertThat(newValue).isEqualTo(1L);
        
        // Increment again
        newValue = userRepository.incrementField("incuser", "followers", 5);
        assertThat(newValue).isEqualTo(6L);
    }
}

