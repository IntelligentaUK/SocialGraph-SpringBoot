package com.intelligenta.socialgraph.service;

import java.util.List;

import com.intelligenta.socialgraph.Verbs;
import com.intelligenta.socialgraph.exception.PostNotFoundException;
import com.intelligenta.socialgraph.model.ActionResponse;
import com.intelligenta.socialgraph.persistence.PostStore;
import com.intelligenta.socialgraph.persistence.ReactionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionServiceTest {

    @Mock private ReactionStore reactions;
    @Mock private PostStore posts;
    @Mock private UserService userService;

    private ActionService actionService;

    @BeforeEach
    void setUp() { actionService = new ActionService(reactions, posts, userService); }

    @Test
    void listActionsRejectsMissingPosts() {
        when(posts.exists("missing")).thenReturn(false);
        assertThrows(PostNotFoundException.class,
            () -> actionService.listActions(Verbs.Action.LIKE, "missing", 0, 10));
    }

    @Test
    void performActionAddsReactionIfAbsent() {
        when(posts.exists("post")).thenReturn(true);
        when(reactions.contains(Verbs.Action.LIKE, "post", "actor")).thenReturn(false);
        String result = actionService.performAction(Verbs.Action.LIKE, "post", "actor");
        assertEquals("liked Post: post", result);
        verify(reactions).add(Verbs.Action.LIKE, "post", "actor");
    }

    @Test
    void performActionSkipsWhenAlreadyPresent() {
        when(posts.exists("post")).thenReturn(true);
        when(reactions.contains(Verbs.Action.LIKE, "post", "actor")).thenReturn(true);
        String result = actionService.performAction(Verbs.Action.LIKE, "post", "actor");
        assertEquals("Already liked Post: post", result);
    }

    @Test
    void listActionsHydratesActorMetadata() {
        when(posts.exists("post")).thenReturn(true);
        when(reactions.listActors(Verbs.Action.LIKE, "post", 0, 2)).thenReturn(List.of("u1"));
        when(userService.getUsername("u1")).thenReturn("alice");
        when(userService.getUserField("u1", "fullname")).thenReturn("Alice");

        ActionResponse r = actionService.listActions(Verbs.Action.LIKE, "post", 0, 2);
        assertEquals(1, r.getCount());
    }
}
