package com.intelligenta.socialgraph.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.intelligenta.socialgraph.ai.ContentModerator;
import com.intelligenta.socialgraph.ai.moderation.NoopModerator;
import com.intelligenta.socialgraph.config.EmbeddingProperties;
import com.intelligenta.socialgraph.config.PersistenceProperties;
import com.intelligenta.socialgraph.exception.ContentBlockedException;
import com.intelligenta.socialgraph.exception.PostNotFoundException;
import com.intelligenta.socialgraph.model.StoredObject;
import com.intelligenta.socialgraph.model.moderation.ModerationDecision;
import com.intelligenta.socialgraph.persistence.CounterStore;
import com.intelligenta.socialgraph.persistence.PostStore;
import com.intelligenta.socialgraph.persistence.TimelineStore;
import com.intelligenta.socialgraph.service.storage.ObjectStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShareServiceTest {

    @Mock private PostStore postStore;
    @Mock private TimelineStore timelineStore;
    @Mock private CounterStore counterStore;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private ZSetOperations<String, String> zSetOperations;
    @SuppressWarnings("rawtypes")
    @Mock private StreamOperations streamOperations;
    @Mock private ObjectStorageService objectStorageService;
    @Mock private UserService userService;

    private ShareService shareService;
    private EmbeddingProperties embeddingProperties;
    private ContentModerator moderator;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        lenient().when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        lenient().when(userService.followerUids(anyString())).thenReturn(Set.of());
        embeddingProperties = new EmbeddingProperties();
        moderator = new NoopModerator();
        shareService = new ShareService(postStore, timelineStore, counterStore,
            redisTemplate, objectStorageService, userService,
            embeddingProperties, moderator, new PersistenceProperties());
    }

    @Test
    void shareTextCreatesPostThroughStore() {
        Map<String, String> post = shareService.shareText("u1", "hello");
        assertNotNull(post);
        assertEquals("text", post.get("type"));
        assertEquals("u1", post.get("uid"));
        verify(postStore).create(anyString(), any(), eq(null), eq("u1"), eq("text"));
    }

    @Test
    void sharePhotoWithUrlRecordsPhotoType() {
        Map<String, String> post = shareService.sharePhoto("u1", "caption", "https://cdn/p.png");
        assertEquals("photo", post.get("type"));
        assertEquals("https://cdn/p.png", post.get("url"));
        assertEquals("1", post.get("imageCount"));
    }

    @Test
    void sharePhotoWithBytesUploadsAndStores() {
        when(objectStorageService.upload(any(), anyString(), anyString()))
            .thenReturn(new StoredObject("azure", "k", "https://cdn/u.png", "image/png"));
        byte[] bytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4};
        Map<String, String> post = shareService.sharePhoto("u1", "caption", bytes, "image/jpeg");
        assertEquals("photo", post.get("type"));
        assertEquals("https://cdn/u.png", post.get("url"));
    }

    @Test
    void sharePhotosRejectsTooManyImages() {
        List<byte[]> manyImages = java.util.Collections.nCopies(100, new byte[]{1, 2, 3});
        assertThrows(IllegalArgumentException.class,
            () -> shareService.sharePhotos("u1", "caption", manyImages, null));
    }

    @Test
    void replyToMissingPostThrows() {
        when(postStore.exists("missing")).thenReturn(false);
        assertThrows(PostNotFoundException.class,
            () -> shareService.replyToPost("u1", "missing", "reply"));
    }

    @Test
    void replyToExistingPostRegistersReply() {
        when(postStore.exists("parent")).thenReturn(true);
        Map<String, String> result = shareService.replyToPost("u1", "parent", "reply");
        assertEquals("reply", result.get("type"));
        verify(postStore).addReply(eq("parent"), anyString());
    }

    @Test
    void getPostReturnsStoredFields() {
        when(postStore.get("p1")).thenReturn(Optional.of(Map.of("id", "p1", "type", "text")));
        Map<String, String> r = shareService.getPost("p1");
        assertEquals("p1", r.get("id"));
    }

    @Test
    void getPostThrowsWhenMissing() {
        when(postStore.get("missing")).thenReturn(Optional.empty());
        assertThrows(PostNotFoundException.class, () -> shareService.getPost("missing"));
    }

    @Test
    void updatePostValidatesAuthor() {
        when(postStore.get("p1")).thenReturn(Optional.of(Map.of("id", "p1", "uid", "owner")));
        shareService.updatePost("owner", "p1", "new content");
        verify(postStore).update(eq("p1"), any());
    }

    @Test
    void deletePostValidatesAuthor() {
        when(postStore.get("p1")).thenReturn(Optional.of(Map.of("id", "p1", "uid", "owner")));
        Map<String, String> r = shareService.deletePost("owner", "p1");
        assertEquals("true", r.get("deleted"));
        verify(postStore).delete("p1");
    }

    @Test
    void fanOutPushesPostToFollowerTimelines() {
        when(userService.followerUids("u1")).thenReturn(Set.of("f1", "f2"));
        when(userService.canViewContent(anyString(), eq("u1"))).thenReturn(true);
        when(userService.hasMuted(anyString(), eq("u1"))).thenReturn(false);
        when(userService.hasNegativeKeyword(anyString(), any())).thenReturn(false);
        when(userService.isImageBlocked(anyString(), any())).thenReturn(false);

        shareService.shareText("u1", "hello followers");

        // The author always gets a push + each follower.
        verify(timelineStore).push(eq("u1"), anyString(), anyDouble(), anyDouble(), anyDouble());
        verify(timelineStore).push(eq("f1"), anyString(), anyDouble(), anyDouble(), anyDouble());
        verify(timelineStore).push(eq("f2"), anyString(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void moderatedContentIsBlocked() {
        ModerationDecision decision = mock(ModerationDecision.class);
        when(decision.blocked()).thenReturn(true);
        ContentModerator flagging = new ContentModerator() {
            @Override public ModerationDecision moderate(String content) { return decision; }
            @Override public boolean enabled() { return true; }
            @Override public String providerKey() { return "test"; }
        };
        ShareService svc = new ShareService(postStore, timelineStore, counterStore,
            redisTemplate, objectStorageService, userService,
            embeddingProperties, flagging, new PersistenceProperties());

        assertThrows(ContentBlockedException.class, () -> svc.shareText("u1", "bad"));
    }

    @Test
    void embeddingQueueDisabledWhenInfinispanProvider() {
        PersistenceProperties props = new PersistenceProperties();
        props.setProvider(PersistenceProperties.Provider.INFINISPAN);
        ShareService svc = new ShareService(postStore, timelineStore, counterStore,
            redisTemplate, objectStorageService, userService,
            embeddingProperties, moderator, props);

        svc.shareText("u1", "hello");
        verify(streamOperations, org.mockito.Mockito.never()).add(any());
    }

    private static double anyDouble() { return org.mockito.ArgumentMatchers.anyDouble(); }
    private static <T> T mock(Class<T> c) { return org.mockito.Mockito.mock(c); }
}
