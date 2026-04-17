package com.intelligenta.socialgraph.service;

import com.intelligenta.socialgraph.config.EmbeddingProperties;
import com.intelligenta.socialgraph.exception.PostNotFoundException;
import com.intelligenta.socialgraph.model.StoredObject;
import com.intelligenta.socialgraph.service.storage.ObjectStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShareServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ListOperations<String, String> listOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectStorageService objectStorageService;

    @Mock
    private UserService userService;

    @Mock
    @SuppressWarnings("rawtypes")
    private StreamOperations streamOperations;

    private ShareService shareService;
    private EmbeddingProperties embeddingProperties;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        lenient().when(streamOperations.add(org.mockito.ArgumentMatchers.<MapRecord<String, String, String>>any()))
            .thenReturn(RecordId.of("1-0"));
        embeddingProperties = new EmbeddingProperties();
        shareService = new ShareService(redisTemplate, objectStorageService, userService, embeddingProperties);
    }

    @Test
    void sharePhotoWithBytesUploadsAndStoresPost() throws Exception {
        byte[] imageBytes = TestImages.pngBytes(4, 3);
        when(objectStorageService.upload(any(byte[].class), eq(".png"), eq("image/png")))
            .thenReturn(new StoredObject("azure", "img-1.png", "https://cdn.example/img-1.png", "image/png"));
        when(setOperations.members("user:user-1:followers")).thenReturn(Set.of("follower-1"));
        when(userService.canViewContent("follower-1", "user-1")).thenReturn(true);
        when(userService.hasMuted("follower-1", "user-1")).thenReturn(false);
        when(userService.hasNegativeKeyword("follower-1", List.of("hello", "world"))).thenReturn(false);
        when(userService.isImageBlocked(eq("follower-1"), anyString())).thenReturn(false);
        when(zSetOperations.score("user:social:importance", "user-1")).thenReturn(7.0);
        when(valueOperations.get("user:user-1:connection:edgescore:follower-1")).thenReturn("2.5");
        when(valueOperations.get("user:user-1:connection:edgescore:user-1")).thenReturn("1.0");

        Map<String, String> response = shareService.sharePhoto("user-1", "hello world", imageBytes, "image/png");

        assertEquals("photo", response.get("type"));
        assertEquals("https://cdn.example/img-1.png", response.get("url"));
        assertNotNull(response.get("id"));
        assertNotNull(response.get("duration"));
        verify(redisTemplate).multi();
        verify(redisTemplate).exec();
        verify(listOperations).leftPush("user:user-1:timeline", response.get("id"));
        verify(listOperations).leftPush("user:follower-1:timeline", response.get("id"));
    }

    @Test
    void shareTextSkipsBlockedFollowers() {
        when(setOperations.members("user:user-1:followers")).thenReturn(Set.of("blocked-viewer"));
        when(userService.canViewContent("blocked-viewer", "user-1")).thenReturn(false);
        when(zSetOperations.score("user:social:importance", "user-1")).thenReturn(1.0);
        when(valueOperations.get("user:user-1:connection:edgescore:user-1")).thenReturn("0.0");

        Map<String, String> response = shareService.shareText("user-1", "hello");

        verify(listOperations).leftPush("user:user-1:timeline", response.get("id"));
        verify(listOperations, never()).leftPush("user:blocked-viewer:timeline", response.get("id"));
    }

    @Test
    void replyToPostRequiresExistingParent() {
        when(redisTemplate.hasKey("post:missing")).thenReturn(false);

        assertThrows(PostNotFoundException.class, () -> shareService.replyToPost("user-1", "missing", "reply"));
    }

    @Test
    void getPostReturnsStringifiedHashEntries() {
        Map<Object, Object> post = new LinkedHashMap<>();
        post.put("id", "post-1");
        post.put("content", "hello");
        post.put("updated", null);
        when(hashOperations.entries("post:post-1")).thenReturn(post);

        Map<String, String> result = shareService.getPost("post-1");

        assertEquals("post-1", result.get("id"));
        assertEquals("hello", result.get("content"));
        assertEquals(null, result.get("updated"));
    }

    @Test
    void updatePostChecksAuthorshipAndReturnsUpdatedProfile() {
        Map<Object, Object> existing = Map.of("id", "post-1", "uid", "author-1", "content", "old");
        when(hashOperations.entries("post:post-1")).thenReturn(existing);

        Map<String, String> result = shareService.updatePost("author-1", "post-1", "new");

        assertEquals("new", result.get("content"));
        verify(userService).ensureAuthor("author-1", "author-1");
        verify(hashOperations).put("post:post-1", "content", "new");
        verify(hashOperations).put(eq("post:post-1"), eq("updated"), anyString());
    }

    @Test
    void deletePostDeletesRedisKeyAndReturnsConfirmation() {
        Map<Object, Object> existing = Map.of("id", "post-1", "uid", "author-1");
        when(hashOperations.entries("post:post-1")).thenReturn(existing);

        Map<String, String> result = shareService.deletePost("author-1", "post-1");

        assertEquals("true", result.get("deleted"));
        assertEquals("post-1", result.get("uuid"));
        verify(redisTemplate).delete("post:post-1");
    }

    @Test
    void getWordsReturnsUniqueWordSequence() {
        assertEquals(List.of("Hello", "world", "123"), ShareService.getWords("Hello, world! Hello 123"));
    }

    @Test
    void sharePhotosWritesImagesListAndImageCount() throws Exception {
        byte[] img1 = TestImages.pngBytes(4, 3);
        byte[] img2 = TestImages.pngBytes(5, 3);
        when(objectStorageService.upload(eq(img1), eq(".png"), eq("image/png")))
            .thenReturn(new StoredObject("azure", "k1.png", "https://cdn.example/k1.png", "image/png"));
        when(objectStorageService.upload(eq(img2), eq(".png"), eq("image/png")))
            .thenReturn(new StoredObject("azure", "k2.png", "https://cdn.example/k2.png", "image/png"));
        when(setOperations.members("user:user-1:followers")).thenReturn(Set.of());
        when(zSetOperations.score("user:social:importance", "user-1")).thenReturn(0.0);
        when(valueOperations.get("user:user-1:connection:edgescore:user-1")).thenReturn("0.0");

        Map<String, String> response = shareService.sharePhotos(
            "user-1", "two pics", List.of(img1, img2), List.of("image/png", "image/png"));

        String postId = response.get("id");
        assertEquals("photo", response.get("type"));
        assertEquals("https://cdn.example/k1.png", response.get("url"));
        assertEquals("2", response.get("imageCount"));
        verify(listOperations).rightPushAll(
            "post:" + postId + ":images",
            "https://cdn.example/k1.png", "https://cdn.example/k2.png");
    }

    @Test
    void sharePhotosRejectsOverCap() throws Exception {
        byte[] img = TestImages.pngBytes(2, 2);
        embeddingProperties.setMaxImagesPerPost(3);
        List<byte[]> tooMany = List.of(img, img, img, img);
        assertThrows(IllegalArgumentException.class,
            () -> shareService.sharePhotos("user-1", "hello", tooMany, List.of()));
    }

    @Test
    void shareTextXAddsToEmbeddingQueue() {
        when(setOperations.members("user:user-1:followers")).thenReturn(Set.of());
        when(zSetOperations.score("user:social:importance", "user-1")).thenReturn(0.0);
        when(valueOperations.get("user:user-1:connection:edgescore:user-1")).thenReturn("0.0");

        Map<String, String> resp = shareService.shareText("user-1", "hello world");

        ArgumentCaptor<MapRecord<String, String, String>> captor = ArgumentCaptor.forClass(MapRecord.class);
        verify(streamOperations).add(captor.capture());
        MapRecord<String, String, String> rec = captor.getValue();
        assertEquals("embedding:queue", rec.getStream());
        assertEquals(resp.get("id"), rec.getValue().get("postId"));
        assertEquals("user-1", rec.getValue().get("authorUid"));
    }

    @Test
    void shareVideoDoesNotXAddToEmbeddingQueue() {
        when(setOperations.members("user:user-1:followers")).thenReturn(Set.of());
        when(zSetOperations.score("user:social:importance", "user-1")).thenReturn(0.0);
        when(valueOperations.get("user:user-1:connection:edgescore:user-1")).thenReturn("0.0");

        shareService.shareVideo("user-1", "look at my video", "https://cdn.example/v.mp4");

        verify(streamOperations, never()).add(org.mockito.ArgumentMatchers.<MapRecord<String, String, String>>any());
    }

    private static final class TestImages {
        private TestImages() {
        }

        private static byte[] pngBytes(int width, int height) throws Exception {
            java.awt.image.BufferedImage image =
                new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        }
    }
}
