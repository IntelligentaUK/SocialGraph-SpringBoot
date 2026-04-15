package com.intelligenta.socialgraph.service;

import com.intelligenta.socialgraph.Verbs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ListOperations<String, String> listOperations;

    @Mock
    private UserService userService;

    private ActionService actionService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        actionService = new ActionService(redisTemplate, userService);
    }

    @Test
    void performActionDoesNotDuplicateExistingActor() {
        when(redisTemplate.hasKey("post:post-1")).thenReturn(true);
        when(hashOperations.get("post:post-1:user-1:", "like")).thenReturn("1");

        String result = actionService.performAction(Verbs.Action.LIKE, "post-1", "user-1");

        assertEquals("Already liked Post: post-1", result);
        verify(listOperations, never()).leftPush("post:post-1likes:", "user-1");
    }

    @Test
    void reverseActionClearsAllMatchingEntriesOnceActorHasActed() {
        when(redisTemplate.hasKey("post:post-1")).thenReturn(true);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(hashOperations.get("post:post-1:user-1:", "like")).thenReturn("1");

        String result = actionService.reverseAction(Verbs.Action.LIKE, "post-1", "user-1");

        assertEquals("Unliked Post: post-1", result);
        verify(listOperations).remove("post:post-1likes:", 0, "user-1");
        verify(hashOperations).delete("post:post-1:user-1:", "like");
    }

    @Test
    void performActionStoresActorWhenActionIsNew() {
        when(redisTemplate.hasKey("post:post-2")).thenReturn(true);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(hashOperations.get("post:post-2:user-2:", "share")).thenReturn(null);

        String result = actionService.performAction(Verbs.Action.SHARE, "post-2", "user-2");

        assertEquals("shared Post: post-2", result);
        verify(listOperations).leftPush("post:post-2shares:", "user-2");
        verify(hashOperations).put("post:post-2:user-2:", "share", "1");
    }

    @Test
    void listActionsBuildsActorPayloads() {
        when(redisTemplate.hasKey("post:post-3")).thenReturn(true);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range("post:post-3loves:", 0, 1)).thenReturn(List.of("user-1", "user-2"));
        when(userService.getUsername("user-1")).thenReturn("alpha");
        when(userService.getUserField("user-1", "fullname")).thenReturn("Alpha User");
        when(userService.getUsername("user-2")).thenReturn("beta");
        when(userService.getUserField("user-2", "fullname")).thenReturn("Beta User");

        var response = actionService.listActions(Verbs.Action.LOVE, "post-3", 0, 2);

        assertEquals("loves", response.getActionType());
        assertEquals(2, response.getCount());
        assertEquals("alpha", response.getActors().getFirst().getUsername());
        assertEquals("Beta User", response.getActors().get(1).getDisplayName());
    }
}
