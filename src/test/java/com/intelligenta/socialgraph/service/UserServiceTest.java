package com.intelligenta.socialgraph.service;

import com.intelligenta.socialgraph.config.AppProperties;
import com.intelligenta.socialgraph.exception.CannotFollowSelfException;
import com.intelligenta.socialgraph.exception.InvalidCredentialsException;
import com.intelligenta.socialgraph.exception.UserNotFoundException;
import com.intelligenta.socialgraph.model.MemberInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private UserService userService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        userService = new UserService(redisTemplate, new AppProperties());
    }

    @Test
    void followUpdatesCountersOnUsernameHashes() {
        when(hashOperations.hasKey("user:uid", "target-uid")).thenReturn(true);
        when(hashOperations.get("user:uid", "target-uid")).thenReturn("target-user");
        when(hashOperations.get("user:uid", "auth-uid")).thenReturn("auth-user");
        when(setOperations.add("user:target-uid:followers", "auth-uid")).thenReturn(1L);

        userService.follow("auth-uid", "target-uid", null);

        verify(setOperations).add("user:auth-uid:following", "target-uid");
        verify(hashOperations).increment("user:target-user", "followers", 1L);
        verify(hashOperations).increment("user:auth-user", "following", 1L);
    }

    @Test
    void followRejectsSelfFollow() {
        when(hashOperations.hasKey("user:uid", "same")).thenReturn(true);
        assertThrows(CannotFollowSelfException.class, () -> userService.follow("same", "same", null));
    }

    @Test
    void activateAccountReturnsFalseForUnknownToken() {
        when(valueOperations.get("user:activations:missing:uid")).thenReturn(null);

        Map<String, String> result = userService.activateAccount("missing");

        assertEquals("false", result.get("activated"));
    }

    @Test
    void blockRemovesExistingFollowRelationships() {
        when(hashOperations.hasKey("user:uid", "target-uid")).thenReturn(true);
        when(hashOperations.get("user:uid", "target-uid")).thenReturn("target-user");
        when(hashOperations.get("user:uid", "auth-uid")).thenReturn("auth-user");
        when(setOperations.add("user:auth-uid:blocked", "target-uid")).thenReturn(1L);
        doReturn(1L).when(setOperations).remove("user:target-uid:followers", "auth-uid");
        doReturn(1L).when(setOperations).remove("user:auth-uid:following", "target-uid");
        doReturn(0L).when(setOperations).remove("user:auth-uid:followers", "target-uid");

        boolean changed = userService.block("auth-uid", "target-uid");

        assertTrue(changed);
        verify(setOperations).remove("user:auth-uid:following", "target-uid");
        verify(hashOperations).increment("user:target-user", "followers", -1L);
        verify(hashOperations).increment("user:auth-user", "following", -1L);
    }

    @Test
    void getProfileIncludesRelationshipFlagsAndCounters() {
        when(hashOperations.get("user:uid", "target-uid")).thenReturn("target-user");
        when(hashOperations.get("user:target-user", "fullname")).thenReturn("Target User");
        when(hashOperations.get("user:target-user", "bio")).thenReturn("Bio");
        when(hashOperations.get("user:target-user", "profilePicture")).thenReturn("pic");
        when(hashOperations.get("user:target-user", "created")).thenReturn("123");
        when(hashOperations.get("user:target-user", "followers")).thenReturn("10");
        when(hashOperations.get("user:target-user", "following")).thenReturn("5");
        when(setOperations.isMember("user:viewer-uid:blocked", "target-uid")).thenReturn(true);
        when(setOperations.isMember("user:viewer-uid:muted", "target-uid")).thenReturn(false);
        when(setOperations.isMember("user:target-uid:blocked", "viewer-uid")).thenReturn(true);

        Map<String, Object> profile = userService.getProfile("target-uid", "viewer-uid");

        assertEquals("target-user", profile.get("username"));
        assertEquals("10", profile.get("followers"));
        assertEquals(true, profile.get("isBlocked"));
        assertEquals(true, profile.get("blocksViewer"));
    }

    @Test
    void searchUsersFiltersAndPaginatesByUsernameAndFullname() {
        when(hashOperations.entries("user:uid")).thenReturn(Map.of("u1", "alice", "u2", "bob"));
        doReturn("alice").when(hashOperations).get("user:uid", "u1");
        doReturn("bob").when(hashOperations).get("user:uid", "u2");
        when(hashOperations.get("user:alice", "fullname")).thenReturn("Alice Smith");
        when(hashOperations.get("user:bob", "fullname")).thenReturn("Robert Jones");

        List<MemberInfo> results = userService.searchUsers("ali", 0, 10);

        assertEquals(1, results.size());
        assertEquals("alice", results.getFirst().getUsername());
        assertEquals("Alice Smith", results.getFirst().getFullname());
    }

    @Test
    void searchUsersReturnsEmptyPageWhenIndexIsOutOfBounds() {
        when(hashOperations.entries("user:uid")).thenReturn(Map.of("u1", "alice"));

        assertTrue(userService.searchUsers("alice", 5, 2).isEmpty());
    }

    @Test
    void loginRejectsUnknownCredentials() {
        when(hashOperations.multiGet("user:ghost", List.of("passwordHash", "salt", "poly"))).thenReturn(List.of());

        assertThrows(InvalidCredentialsException.class, () -> userService.login("ghost", "password"));
    }

    @Test
    void canViewContentReturnsFalseWhenEitherSideHasBlocked() {
        when(setOperations.isMember("user:viewer:blocked", "actor")).thenReturn(false);
        when(setOperations.isMember("user:actor:blocked", "viewer")).thenReturn(true);

        assertFalse(userService.canViewContent("viewer", "actor"));
    }

    @Test
    void updateProfileRejectsUnknownUsers() {
        when(hashOperations.get("user:uid", "missing")).thenReturn(null);

        assertThrows(UserNotFoundException.class, () -> userService.updateProfile("missing", "Name", null, null));
    }
}
