package com.intelligenta.socialgraph.service;

import com.intelligenta.socialgraph.model.TimelineResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimelineServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private ListOperations<String, String> listOperations;

    @Mock
    private UserService userService;

    private TimelineService timelineService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
        timelineService = new TimelineService(redisTemplate, userService);
    }

    @Test
    void socialImportanceTimelineUsesDescendingOrderAndFiltersBlockedImages() {
        LinkedHashMap<Object, Object> post = new LinkedHashMap<>();
        post.put("id", "post-1");
        post.put("uid", "actor-1");
        post.put("type", "photo");
        post.put("content", "hello world");
        post.put("imageHash", "hash-1");

        when(zSetOperations.reverseRange("user:viewer-1:timeline:everyone:importance", 0L, 9L))
            .thenReturn(Set.of("post-1"));
        when(hashOperations.entries("post:post-1")).thenReturn(post);
        when(userService.canViewContent("viewer-1", "actor-1")).thenReturn(true);
        when(userService.hasNegativeKeyword("viewer-1", ShareService.getWords("hello world"))).thenReturn(false);
        when(userService.isImageBlocked("viewer-1", "hash-1")).thenReturn(true);

        TimelineResponse response = timelineService.getSocialImportanceTimeline(
            "viewer-1", 0, 10, TimelineService.Importance.EVERYONE);

        assertEquals(0, response.getCount());
        verify(zSetOperations).reverseRange("user:viewer-1:timeline:everyone:importance", 0L, 9L);
    }

    @Test
    void fifoTimelineReturnsVisibleEntries() {
        LinkedHashMap<Object, Object> post = new LinkedHashMap<>();
        post.put("id", "post-2");
        post.put("uid", "actor-2");
        post.put("type", "text");
        post.put("content", "visible content");
        post.put("created", "100");

        when(listOperations.range("user:viewer-2:timeline", 0L, 0L)).thenReturn(List.of("post-2"));
        when(hashOperations.entries("post:post-2")).thenReturn(post);
        when(userService.canViewContent("viewer-2", "actor-2")).thenReturn(true);
        when(userService.hasNegativeKeyword("viewer-2", ShareService.getWords("visible content"))).thenReturn(false);
        when(userService.isImageBlocked("viewer-2", null)).thenReturn(false);
        when(userService.getUsername("actor-2")).thenReturn("actor");
        when(userService.getUserField("actor-2", "fullname")).thenReturn("Actor User");

        TimelineResponse response = timelineService.getFifoTimeline("viewer-2", 0, 1);

        assertEquals(1, response.getCount());
        assertEquals("post-2", response.getEntities().getFirst().getUuid());
        assertEquals("actor", response.getEntities().getFirst().getActorUsername());
    }

    @Test
    void getPostThrowsWhenTimelineEntryCannotBeResolved() {
        when(hashOperations.entries("post:missing")).thenReturn(Map.of());

        assertThrows(
            com.intelligenta.socialgraph.exception.PostNotFoundException.class,
            () -> timelineService.getPost("viewer", "missing")
        );
    }

    @Test
    void generatePostPopulatesImageUrlsFromImagesList() {
        LinkedHashMap<Object, Object> post = new LinkedHashMap<>();
        post.put("id", "post-multi");
        post.put("uid", "actor-m");
        post.put("type", "photo");
        post.put("content", "three pics");
        post.put("url", "https://cdn.example/a.png");
        post.put("imageCount", "3");

        when(listOperations.range("user:viewer-m:timeline", 0L, 0L)).thenReturn(List.of("post-multi"));
        when(hashOperations.entries("post:post-multi")).thenReturn(post);
        when(listOperations.range("post:post-multi:images", 0L, -1L))
            .thenReturn(List.of("https://cdn.example/a.png", "https://cdn.example/b.png", "https://cdn.example/c.png"));
        when(userService.canViewContent("viewer-m", "actor-m")).thenReturn(true);
        when(userService.hasNegativeKeyword("viewer-m", ShareService.getWords("three pics"))).thenReturn(false);
        when(userService.isImageBlocked("viewer-m", null)).thenReturn(false);
        when(userService.getUsername("actor-m")).thenReturn("actor-m");
        when(userService.getUserField("actor-m", "fullname")).thenReturn("Actor M");

        TimelineResponse response = timelineService.getFifoTimeline("viewer-m", 0, 1);

        var entry = response.getEntities().getFirst();
        assertEquals(3, entry.getImageUrls().size());
        assertEquals("https://cdn.example/a.png", entry.getImageUrls().getFirst());
        assertEquals("https://cdn.example/c.png", entry.getImageUrls().get(2));
    }

    @Test
    void generatePostBackfillsImageUrlsForLegacySingleImagePosts() {
        LinkedHashMap<Object, Object> post = new LinkedHashMap<>();
        post.put("id", "post-legacy");
        post.put("uid", "actor-l");
        post.put("type", "photo");
        post.put("content", "legacy");
        post.put("url", "https://cdn.example/legacy.jpg");
        // NOTE: no imageCount field — simulating a pre-multi-image post

        when(listOperations.range("user:viewer-l:timeline", 0L, 0L)).thenReturn(List.of("post-legacy"));
        when(hashOperations.entries("post:post-legacy")).thenReturn(post);
        when(userService.canViewContent("viewer-l", "actor-l")).thenReturn(true);
        when(userService.hasNegativeKeyword("viewer-l", ShareService.getWords("legacy"))).thenReturn(false);
        when(userService.isImageBlocked("viewer-l", null)).thenReturn(false);
        when(userService.getUsername("actor-l")).thenReturn("actor-l");
        when(userService.getUserField("actor-l", "fullname")).thenReturn("Actor L");

        TimelineResponse response = timelineService.getFifoTimeline("viewer-l", 0, 1);

        var entry = response.getEntities().getFirst();
        assertEquals(List.of("https://cdn.example/legacy.jpg"), entry.getImageUrls());
    }

    @Test
    void getRepliesReturnsResolvedReplyEntries() {
        LinkedHashMap<Object, Object> reply = new LinkedHashMap<>();
        reply.put("id", "reply-1");
        reply.put("uid", "actor-3");
        reply.put("type", "reply");
        reply.put("content", "reply body");

        when(listOperations.range("post:post-3:replies", 0L, 1L)).thenReturn(List.of("reply-1"));
        when(hashOperations.entries("post:reply-1")).thenReturn(reply);
        when(userService.canViewContent("viewer-3", "actor-3")).thenReturn(true);
        when(userService.hasNegativeKeyword("viewer-3", ShareService.getWords("reply body"))).thenReturn(false);
        when(userService.isImageBlocked("viewer-3", null)).thenReturn(false);
        when(userService.getUsername("actor-3")).thenReturn("reply-user");
        when(userService.getUserField("actor-3", "fullname")).thenReturn("Reply User");

        TimelineResponse response = timelineService.getReplies("viewer-3", "post-3", 0, 2);

        assertEquals(1, response.getCount());
        assertEquals("reply-1", response.getEntities().getFirst().getUuid());
    }
}
