package com.intelligenta.socialgraph.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.intelligenta.socialgraph.config.AppProperties;
import com.intelligenta.socialgraph.exception.CannotFollowSelfException;
import com.intelligenta.socialgraph.exception.InvalidCredentialsException;
import com.intelligenta.socialgraph.exception.UserNotFoundException;
import com.intelligenta.socialgraph.model.MemberInfo;
import com.intelligenta.socialgraph.persistence.ContentFilterStore;
import com.intelligenta.socialgraph.persistence.RelationStore;
import com.intelligenta.socialgraph.persistence.RelationStore.Relation;
import com.intelligenta.socialgraph.persistence.TokenStore;
import com.intelligenta.socialgraph.persistence.UserStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserStore users;
    @Mock private RelationStore relations;
    @Mock private ContentFilterStore filters;
    @Mock private TokenStore tokens;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(users, relations, filters, tokens, new AppProperties());
    }

    @Test
    void followUpdatesCountersOnUsernameHashes() {
        when(users.uidExists("target-uid")).thenReturn(true);
        when(users.findUsernameByUid("target-uid")).thenReturn(Optional.of("target-user"));
        when(users.findUsernameByUid("auth-uid")).thenReturn(Optional.of("auth-user"));
        when(relations.add("target-uid", Relation.FOLLOWERS, "auth-uid")).thenReturn(true);

        userService.follow("auth-uid", "target-uid", null);

        verify(relations).add("auth-uid", Relation.FOLLOWING, "target-uid");
        verify(users).incrementField("target-user", "followers", 1L);
        verify(users).incrementField("auth-user", "following", 1L);
    }

    @Test
    void followRejectsSelfFollow() {
        when(users.uidExists("same")).thenReturn(true);
        assertThrows(CannotFollowSelfException.class, () -> userService.follow("same", "same", null));
    }

    @Test
    void activateAccountReturnsFalseForUnknownToken() {
        when(users.consumeActivationToken("missing")).thenReturn(Optional.empty());
        assertEquals("false", userService.activateAccount("missing").get("activated"));
    }

    @Test
    void blockRemovesExistingFollowRelationships() {
        when(users.uidExists("target-uid")).thenReturn(true);
        lenient().when(users.findUsernameByUid("target-uid")).thenReturn(Optional.of("target-user"));
        lenient().when(users.findUsernameByUid("auth-uid")).thenReturn(Optional.of("auth-user"));
        when(relations.add("auth-uid", Relation.BLOCKED, "target-uid")).thenReturn(true);
        lenient().when(relations.remove("target-uid", Relation.FOLLOWERS, "auth-uid")).thenReturn(true);
        lenient().when(relations.remove("auth-uid", Relation.FOLLOWERS, "target-uid")).thenReturn(false);

        assertTrue(userService.block("auth-uid", "target-uid"));
        verify(relations).remove("auth-uid", Relation.FOLLOWING, "target-uid");
        verify(users).incrementField("target-user", "followers", -1L);
        verify(users).incrementField("auth-user", "following", -1L);
    }

    @Test
    void getProfileIncludesRelationshipFlagsAndCounters() {
        when(users.findUsernameByUid("target-uid")).thenReturn(Optional.of("target-user"));
        when(users.getField("target-user", "fullname")).thenReturn(Optional.of("Target User"));
        when(users.getField("target-user", "bio")).thenReturn(Optional.of("Bio"));
        when(users.getField("target-user", "profilePicture")).thenReturn(Optional.of("pic"));
        when(users.getField("target-user", "created")).thenReturn(Optional.of("123"));
        when(users.getField("target-user", "followers")).thenReturn(Optional.of("10"));
        when(users.getField("target-user", "following")).thenReturn(Optional.of("5"));
        when(relations.contains("viewer-uid", Relation.BLOCKED, "target-uid")).thenReturn(true);
        when(relations.contains("viewer-uid", Relation.MUTED, "target-uid")).thenReturn(false);
        when(relations.contains("target-uid", Relation.BLOCKED, "viewer-uid")).thenReturn(true);

        Map<String, Object> profile = userService.getProfile("target-uid", "viewer-uid");
        assertEquals("target-user", profile.get("username"));
        assertEquals("10", profile.get("followers"));
        assertEquals(true, profile.get("isBlocked"));
        assertEquals(true, profile.get("blocksViewer"));
    }

    @Test
    void searchUsersFiltersAndPaginatesByUsernameAndFullname() {
        when(users.allUidToUsername()).thenReturn(Map.of("u1", "alice", "u2", "bob"));
        lenient().when(users.findUsernameByUid("u1")).thenReturn(Optional.of("alice"));
        lenient().when(users.findUsernameByUid("u2")).thenReturn(Optional.of("bob"));
        when(users.getField("alice", "fullname")).thenReturn(Optional.of("Alice Smith"));
        lenient().when(users.getField("bob", "fullname")).thenReturn(Optional.of("Robert Jones"));

        List<MemberInfo> results = userService.searchUsers("ali", 0, 10);
        assertEquals(1, results.size());
        assertEquals("alice", results.getFirst().getUsername());
        assertEquals("Alice Smith", results.getFirst().getFullname());
    }

    @Test
    void searchUsersReturnsEmptyPageWhenIndexIsOutOfBounds() {
        when(users.allUidToUsername()).thenReturn(Map.of("u1", "alice"));
        assertTrue(userService.searchUsers("alice", 5, 2).isEmpty());
    }

    @Test
    void loginRejectsUnknownCredentials() {
        when(users.getFields("ghost", List.of("passwordHash", "salt", "poly")))
            .thenReturn(List.of(Optional.empty(), Optional.empty(), Optional.empty()));
        assertThrows(InvalidCredentialsException.class, () -> userService.login("ghost", "password"));
    }

    @Test
    void canViewContentReturnsFalseWhenEitherSideHasBlocked() {
        when(relations.contains("viewer", Relation.BLOCKED, "actor")).thenReturn(false);
        when(relations.contains("actor", Relation.BLOCKED, "viewer")).thenReturn(true);
        assertFalse(userService.canViewContent("viewer", "actor"));
    }

    @Test
    void updateProfileRejectsUnknownUsers() {
        when(users.findUsernameByUid("missing")).thenReturn(Optional.empty());
        assertThrows(UserNotFoundException.class, () -> userService.updateProfile("missing", "Name", null, null));
    }
}
