package com.intelligenta.socialgraph.service;

import com.intelligenta.socialgraph.ai.ContentModerator;
import com.intelligenta.socialgraph.config.EmbeddingProperties;
import com.intelligenta.socialgraph.config.PersistenceProperties;
import com.intelligenta.socialgraph.exception.ContentBlockedException;
import com.intelligenta.socialgraph.exception.PostNotFoundException;
import com.intelligenta.socialgraph.model.StoredObject;
import com.intelligenta.socialgraph.model.moderation.ModerationDecision;
import com.intelligenta.socialgraph.service.storage.ObjectStorageService;
import com.intelligenta.socialgraph.util.ImagePayloads;
import com.intelligenta.socialgraph.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for sharing posts and status updates.
 */
@Service
public class ShareService {

    private static final Logger log = LoggerFactory.getLogger(ShareService.class);

    static final String EMBEDDING_QUEUE = "embedding:queue";

    private final StringRedisTemplate redisTemplate;
    private final ObjectStorageService objectStorageService;
    private final UserService userService;
    private final EmbeddingProperties embeddingProperties;
    private final ContentModerator moderator;
    private final boolean embeddingQueueEnabled;

    public ShareService(StringRedisTemplate redisTemplate,
                        ObjectStorageService objectStorageService,
                        UserService userService,
                        EmbeddingProperties embeddingProperties,
                        ContentModerator moderator,
                        PersistenceProperties persistenceProperties) {
        this.redisTemplate = redisTemplate;
        this.objectStorageService = objectStorageService;
        this.userService = userService;
        this.embeddingProperties = embeddingProperties;
        this.moderator = moderator;
        // Redis Streams are Redis-specific; Infinispan RESP doesn't implement
        // them. Phase I-J wires an Infinispan-native replacement, after which
        // this flag is replaced by an EmbeddingQueue abstraction.
        this.embeddingQueueEnabled =
            persistenceProperties.getProvider() == PersistenceProperties.Provider.REDIS;
    }

    /**
     * Share a photo with URL.
     */
    public Map<String, String> sharePhoto(String authenticatedUser, String content, String url) {
        List<String> urls = url == null ? null : List.of(url);
        return createStatusUpdate(authenticatedUser, content, "photo", urls, null, null, null);
    }

    /**
     * Share a photo with bytes.
     */
    public Map<String, String> sharePhoto(String authenticatedUser, String content, byte[] bytes) {
        return sharePhoto(authenticatedUser, content, bytes, null);
    }

    /**
     * Share a photo with bytes and an optional content type hint.
     */
    public Map<String, String> sharePhoto(String authenticatedUser, String content, byte[] bytes, String contentType) {
        List<String> urls = null;
        String imageHash = null;

        if (bytes != null && bytes.length > 0) {
            ImagePayloads.ImagePayload imagePayload = ImagePayloads.fromBytes(bytes, contentType);
            imageHash = Util.getMD5(bytes);
            StoredObject storedObject = objectStorageService.upload(
                bytes,
                imagePayload.extension(),
                imagePayload.mimeType()
            );
            urls = List.of(storedObject.objectUrl());
        }

        return createStatusUpdate(authenticatedUser, content, "photo", urls, imageHash, null, null);
    }

    /**
     * Share multiple photos in a single post. Uploads each byte-array to object
     * storage (in input order), records the ordered list on {@code post:<id>:images},
     * and sets {@code imageCount} on the post hash. The first image's MD5 is
     * stored as the legacy {@code imageHash} for image-block filtering.
     */
    public Map<String, String> sharePhotos(String authenticatedUser,
                                           String content,
                                           List<byte[]> imageBytes,
                                           List<String> contentTypes) {
        if (imageBytes == null || imageBytes.isEmpty()) {
            return createStatusUpdate(authenticatedUser, content, "photo", null, null, null, null);
        }
        int max = embeddingProperties.getMaxImagesPerPost();
        if (imageBytes.size() > max) {
            throw new IllegalArgumentException("too_many_images");
        }

        List<String> urls = new ArrayList<>(imageBytes.size());
        String firstHash = null;
        for (int i = 0; i < imageBytes.size(); i++) {
            byte[] b = imageBytes.get(i);
            if (b == null || b.length == 0) continue;
            String ct = (contentTypes != null && i < contentTypes.size()) ? contentTypes.get(i) : null;
            ImagePayloads.ImagePayload p = ImagePayloads.fromBytes(b, ct);
            if (firstHash == null) firstHash = Util.getMD5(b);
            StoredObject so = objectStorageService.upload(b, p.extension(), p.mimeType());
            urls.add(so.objectUrl());
        }
        return createStatusUpdate(authenticatedUser, content, "photo", urls, firstHash, null, null);
    }

    /**
     * Share a video.
     */
    public Map<String, String> shareVideo(String authenticatedUser, String content, String url) {
        List<String> urls = url == null ? null : List.of(url);
        return createStatusUpdate(authenticatedUser, content, "video", urls, null, null, null);
    }

    /**
     * Share an audio clip.
     */
    public Map<String, String> shareAudio(String authenticatedUser, String content, String url) {
        List<String> urls = url == null ? null : List.of(url);
        return createStatusUpdate(authenticatedUser, content, "audio", urls, null, null, null);
    }

    /**
     * Share a text-only post.
     */
    public Map<String, String> shareText(String authenticatedUser, String content) {
        return createStatusUpdate(authenticatedUser, content, "text", null, null, null, null);
    }

    /**
     * Reply to an existing post.
     */
    public Map<String, String> replyToPost(String authenticatedUser, String parentPostId, String content) {
        ensurePostExists(parentPostId);
        return createStatusUpdate(authenticatedUser, content, "reply", null, null, parentPostId, null);
    }

    /**
     * Reshare an existing post.
     */
    public Map<String, String> resharePost(String authenticatedUser, String postId, String content) {
        ensurePostExists(postId);
        return createStatusUpdate(authenticatedUser, content, "reshare", null, null, null, postId);
    }

    int getMaxImagesPerPost() {
        return embeddingProperties.getMaxImagesPerPost();
    }

    /**
     * Share/reshare an existing post.
     */
    public Map<String, String> reshare(String authenticatedUser, String postId) {
        if (authenticatedUser == null) {
            return null;
        }

        long startTime = System.currentTimeMillis();
        Map<Object, Object> postObj = redisTemplate.opsForHash().entries("post:" + postId);
        Map<String, String> post = new HashMap<>();
        postObj.forEach((k, v) -> post.put((String) k, (String) v));

        if (!post.isEmpty()) {
            List<String> keywords = getWords(post.get("content"));
            pushGraph(authenticatedUser, postId, keywords, readImageHash(post));
            post.put("duration", String.valueOf(System.currentTimeMillis() - startTime));
        }

        return post;
    }

    public Map<String, String> getPost(String postId) {
        Map<Object, Object> postObject = redisTemplate.opsForHash().entries("post:" + postId);
        if (postObject.isEmpty()) {
            throw new PostNotFoundException("Post not found");
        }

        Map<String, String> post = new HashMap<>();
        postObject.forEach((key, value) -> post.put(String.valueOf(key), value != null ? String.valueOf(value) : null));
        return post;
    }

    public Map<String, String> updatePost(String authenticatedUser, String postId, String content) {
        Map<String, String> post = getPost(postId);
        userService.ensureAuthor(authenticatedUser, post.get("uid"));

        redisTemplate.opsForHash().put("post:" + postId, "content", content);
        String updated = Util.unixtime();
        redisTemplate.opsForHash().put("post:" + postId, "updated", updated);
        post.put("content", content);
        post.put("updated", updated);
        return post;
    }

    public Map<String, String> deletePost(String authenticatedUser, String postId) {
        Map<String, String> post = getPost(postId);
        userService.ensureAuthor(authenticatedUser, post.get("uid"));

        redisTemplate.delete("post:" + postId);

        Map<String, String> response = new HashMap<>();
        response.put("deleted", "true");
        response.put("uuid", postId);
        return response;
    }

    private Map<String, String> createStatusUpdate(String authenticatedUser, String content,
                                                   String type, List<String> imageUrls, String imageHash,
                                                   String parentPostId, String sharedPostId) {
        if (authenticatedUser == null) {
            return null;
        }

        if (moderator.enabled() && content != null && !content.isBlank()) {
            ModerationDecision decision = moderator.moderate(content);
            if (decision.blocked()) {
                throw new ContentBlockedException(decision);
            }
        }

        long startTime = System.currentTimeMillis();
        String postId = Util.UUID();

        String primaryUrl = (imageUrls != null && !imageUrls.isEmpty()) ? imageUrls.get(0) : null;
        int imageCount = imageUrls == null ? 0 : imageUrls.size();

        Map<String, String> post = new HashMap<>();
        post.put("id", postId);
        post.put("type", type);
        post.put("uid", authenticatedUser);
        post.put("created", Util.unixtime());
        putIfPresent(post, "content", content);
        putIfPresent(post, "url", primaryUrl);
        if (imageCount > 0) {
            post.put("imageCount", Integer.toString(imageCount));
        }

        if (imageHash != null) {
            post.put("imageHash", imageHash);
        }
        if (parentPostId != null) {
            post.put("parentId", parentPostId);
        }
        if (sharedPostId != null) {
            post.put("sharedPostId", sharedPostId);
        }

        List<String> keywords = getWords(content);

        log.info("Creating status update: {}", post);

        // Execute Redis transaction
        redisTemplate.multi();
        redisTemplate.opsForHash().putAll("post:" + postId, post);
        if (imageCount > 1) {
            redisTemplate.opsForList().rightPushAll(
                "post:" + postId + ":images",
                imageUrls.toArray(new String[0]));
        } else if (imageCount == 1) {
            redisTemplate.opsForList().rightPush("post:" + postId + ":images", primaryUrl);
        }
        addPostToTimeline(authenticatedUser, postId, authenticatedUser);

        if (parentPostId != null) {
            redisTemplate.opsForList().leftPush("post:" + parentPostId + ":replies", postId);
        }

        if ("photo".equals(type)) {
            redisTemplate.opsForHash().increment("photos", authenticatedUser, 1);
        } else if ("video".equals(type)) {
            redisTemplate.opsForHash().increment("videos", authenticatedUser, 1);
        } else if ("text".equals(type) || "reply".equals(type) || "reshare".equals(type)) {
            redisTemplate.opsForHash().increment("posts", authenticatedUser, 1);
        }
        redisTemplate.exec();

        pushGraph(authenticatedUser, postId, keywords, imageHash);

        if (embeddingQueueEnabled && shouldEmitEmbedding(type, content, imageCount)) {
            redisTemplate.opsForStream().add(MapRecord.create(
                EMBEDDING_QUEUE,
                Map.of("postId", postId, "authorUid", authenticatedUser)));
        }

        post.remove("imageHash");
        post.put("duration", String.valueOf(System.currentTimeMillis() - startTime));

        return post;
    }

    /**
     * Decides whether the post produces an embedding. Audio and video posts
     * are included (the sidecar's E4B model handles them via
     * {@code /summarize/audio} and {@code /summarize/video}). Text/reply/reshare
     * posts without any content are excluded.
     */
    private static boolean shouldEmitEmbedding(String type, String content, int imageCount) {
        if ("audio".equals(type) || "video".equals(type)) return true;
        boolean hasContent = content != null && !content.isBlank();
        return hasContent || imageCount > 0;
    }

    /**
     * Push post to followers' timelines.
     */
    private void pushGraph(String authenticatedUser, String postId, List<String> keywords, String imageHash) {
        Set<String> followers = redisTemplate.opsForSet().members("user:" + authenticatedUser + ":followers");

        if (followers != null) {
            for (String followerUid : followers) {
                if (shouldDeliver(followerUid, authenticatedUser, keywords, imageHash)) {
                    addPostToTimeline(followerUid, postId, authenticatedUser);
                }
            }
        }
    }

    private boolean negativeKeywordNotFound(String uid, List<String> words) {
        return !userService.hasNegativeKeyword(uid, words);
    }

    private Double getConnectionZScore(String authenticatedUser, String targetUid) {
        String edgeScore = redisTemplate.opsForValue().get(
            "user:" + authenticatedUser + ":connection:edgescore:" + targetUid);
        return edgeScore != null ? Double.parseDouble(edgeScore) : 0.0;
    }

    private Double getSocialImportance(String uid) {
        return redisTemplate.opsForZSet().score("user:social:importance", uid);
    }

    private void addPostToTimeline(String recipientUid, String postId, String authorUid) {
        double everyoneScore = getSocialImportance(authorUid) != null ? getSocialImportance(authorUid) : 0.0;
        redisTemplate.opsForList().leftPush("user:" + recipientUid + ":timeline", postId);
        redisTemplate.opsForZSet().add(
            "user:" + recipientUid + ":timeline:personal:importance",
            postId,
            getConnectionZScore(authorUid, recipientUid)
        );
        redisTemplate.opsForZSet().add(
            "user:" + recipientUid + ":timeline:everyone:importance",
            postId,
            everyoneScore
        );
    }

    private boolean shouldDeliver(String recipientUid, String authorUid, List<String> keywords, String imageHash) {
        if (!userService.canViewContent(recipientUid, authorUid)) {
            return false;
        }
        if (userService.hasMuted(recipientUid, authorUid)) {
            return false;
        }
        if (!negativeKeywordNotFound(recipientUid, keywords)) {
            return false;
        }
        return !userService.isImageBlocked(recipientUid, imageHash);
    }

    private void ensurePostExists(String postId) {
        if (!Boolean.TRUE.equals(redisTemplate.hasKey("post:" + postId))) {
            throw new PostNotFoundException("Post not found");
        }
    }

    private String readImageHash(Map<String, String> post) {
        String imageHash = post.get("imageHash");
        return imageHash != null ? imageHash : post.get("md5");
    }

    private void putIfPresent(Map<String, String> target, String key, String value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    /**
     * Parse words from text content.
     */
    public static List<String> getWords(String text) {
        List<String> words = new ArrayList<>();
        if (text == null) {
            return words;
        }
        
        BreakIterator breakIterator = BreakIterator.getWordInstance();
        breakIterator.setText(text);
        int lastIndex = breakIterator.first();
        while (BreakIterator.DONE != lastIndex) {
            int firstIndex = lastIndex;
            lastIndex = breakIterator.next();
            if (lastIndex != BreakIterator.DONE && Character.isLetterOrDigit(text.charAt(firstIndex))) {
                String word = text.substring(firstIndex, lastIndex);
                if (!words.contains(word)) {
                    words.add(word);
                }
            }
        }
        return words;
    }

    /**
     * Extract hashtags from text.
     */
    public static List<String> getHashTags(String text) {
        List<String> hashTags = new ArrayList<>();
        getWords(text).stream()
            .filter(s -> s.startsWith("#"))
            .forEach(hashTags::add);
        return hashTags;
    }
}
