package com.intelligenta.socialgraph.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.intelligenta.socialgraph.model.TimelineResponse;
import com.intelligenta.socialgraph.persistence.PostStore;
import com.intelligenta.socialgraph.persistence.TimelineStore;
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
class TimelineServiceTest {

    @Mock private TimelineStore timelines;
    @Mock private PostStore posts;
    @Mock private UserService userService;

    private TimelineService timelineService;

    @BeforeEach
    void setUp() { timelineService = new TimelineService(timelines, posts, userService); }

    @Test
    void socialImportanceTimelineFiltersBlockedImages() {
        Map<String, Object> post = Map.of(
            "id", "post-1", "uid", "actor-1", "type", "photo",
            "content", "hello world", "imageHash", "hash-1");

        when(timelines.range("viewer-1", TimelineStore.Kind.EVERYONE_IMPORTANCE, 0, 10))
            .thenReturn(List.of("post-1"));
        when(posts.get("post-1")).thenReturn(Optional.of(post));
        when(userService.canViewContent("viewer-1", "actor-1")).thenReturn(true);
        when(userService.hasNegativeKeyword("viewer-1", ShareService.getWords("hello world"))).thenReturn(false);
        when(userService.isImageBlocked("viewer-1", "hash-1")).thenReturn(true);

        TimelineResponse r = timelineService.getSocialImportanceTimeline(
            "viewer-1", 0, 10, TimelineService.Importance.EVERYONE);

        assertEquals(0, r.getCount());
        verify(timelines).range("viewer-1", TimelineStore.Kind.EVERYONE_IMPORTANCE, 0, 10);
    }

    @Test
    void fifoTimelineReturnsVisibleEntries() {
        Map<String, Object> post = Map.of(
            "id", "post-2", "uid", "actor-2", "type", "text",
            "content", "visible content", "created", "100");

        when(timelines.range("viewer-2", TimelineStore.Kind.FIFO, 0, 1)).thenReturn(List.of("post-2"));
        when(posts.get("post-2")).thenReturn(Optional.of(post));
        when(userService.canViewContent("viewer-2", "actor-2")).thenReturn(true);
        when(userService.hasNegativeKeyword("viewer-2", ShareService.getWords("visible content"))).thenReturn(false);
        when(userService.isImageBlocked("viewer-2", null)).thenReturn(false);
        when(userService.getUsername("actor-2")).thenReturn("actor");
        when(userService.getUserField("actor-2", "fullname")).thenReturn("Actor User");

        TimelineResponse r = timelineService.getFifoTimeline("viewer-2", 0, 1);
        assertEquals(1, r.getCount());
        assertEquals("post-2", r.getEntities().getFirst().getUuid());
        assertEquals("actor", r.getEntities().getFirst().getActorUsername());
    }

    @Test
    void getPostThrowsWhenMissing() {
        when(posts.get("missing")).thenReturn(Optional.empty());
        assertThrows(com.intelligenta.socialgraph.exception.PostNotFoundException.class,
            () -> timelineService.getPost("viewer", "missing"));
    }

    @Test
    void generatePostPopulatesImageUrlsFromImagesList() {
        Map<String, Object> post = Map.of(
            "id", "post-multi", "uid", "actor-m", "type", "photo",
            "content", "three pics", "url", "https://cdn.example/a.png", "imageCount", "3");

        when(timelines.range("viewer-m", TimelineStore.Kind.FIFO, 0, 1)).thenReturn(List.of("post-multi"));
        when(posts.get("post-multi")).thenReturn(Optional.of(post));
        when(posts.images("post-multi")).thenReturn(List.of(
            "https://cdn.example/a.png", "https://cdn.example/b.png", "https://cdn.example/c.png"));
        when(userService.canViewContent("viewer-m", "actor-m")).thenReturn(true);
        when(userService.hasNegativeKeyword("viewer-m", ShareService.getWords("three pics"))).thenReturn(false);
        when(userService.isImageBlocked("viewer-m", null)).thenReturn(false);
        when(userService.getUsername("actor-m")).thenReturn("actor-m");
        when(userService.getUserField("actor-m", "fullname")).thenReturn("Actor M");

        TimelineResponse r = timelineService.getFifoTimeline("viewer-m", 0, 1);
        var entry = r.getEntities().getFirst();
        assertEquals(3, entry.getImageUrls().size());
        assertEquals("https://cdn.example/a.png", entry.getImageUrls().getFirst());
        assertEquals("https://cdn.example/c.png", entry.getImageUrls().get(2));
    }

    @Test
    void generatePostBackfillsImageUrlsForLegacySingleImagePosts() {
        Map<String, Object> post = Map.of(
            "id", "post-legacy", "uid", "actor-l", "type", "photo",
            "content", "legacy", "url", "https://cdn.example/legacy.jpg");

        when(timelines.range("viewer-l", TimelineStore.Kind.FIFO, 0, 1)).thenReturn(List.of("post-legacy"));
        when(posts.get("post-legacy")).thenReturn(Optional.of(post));
        when(userService.canViewContent("viewer-l", "actor-l")).thenReturn(true);
        when(userService.hasNegativeKeyword("viewer-l", ShareService.getWords("legacy"))).thenReturn(false);
        when(userService.isImageBlocked("viewer-l", null)).thenReturn(false);
        when(userService.getUsername("actor-l")).thenReturn("actor-l");
        when(userService.getUserField("actor-l", "fullname")).thenReturn("Actor L");

        TimelineResponse r = timelineService.getFifoTimeline("viewer-l", 0, 1);
        assertEquals(List.of("https://cdn.example/legacy.jpg"), r.getEntities().getFirst().getImageUrls());
    }

    @Test
    void getRepliesReturnsResolvedReplyEntries() {
        Map<String, Object> reply = Map.of(
            "id", "reply-1", "uid", "actor-3", "type", "reply", "content", "reply body");

        when(posts.replies("post-3", 0, 2)).thenReturn(List.of("reply-1"));
        when(posts.get("reply-1")).thenReturn(Optional.of(reply));
        when(userService.canViewContent("viewer-3", "actor-3")).thenReturn(true);
        when(userService.hasNegativeKeyword("viewer-3", ShareService.getWords("reply body"))).thenReturn(false);
        when(userService.isImageBlocked("viewer-3", null)).thenReturn(false);
        when(userService.getUsername("actor-3")).thenReturn("reply-user");
        when(userService.getUserField("actor-3", "fullname")).thenReturn("Reply User");

        TimelineResponse r = timelineService.getReplies("viewer-3", "post-3", 0, 2);
        assertEquals(1, r.getCount());
        assertEquals("reply-1", r.getEntities().getFirst().getUuid());
    }
}
