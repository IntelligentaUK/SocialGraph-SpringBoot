package com.intelligenta.socialgraph.service;

import com.intelligenta.socialgraph.ai.ContentModerator;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Post sharing / fan-out. Refactored in phase I-D to persist through the
 * {@link PostStore}, {@link TimelineStore}, {@link CounterStore}, and the
 * user / content-filter / relation stores via {@link UserService}. Redis
 * Streams + a couple of read-only score lookups (edge scores, global social
 * importance) stay on the direct {@link StringRedisTemplate} for now — they
 * migrate to Infinispan-native primitives in phases I-H (scores) and I-J
 * (streams).
 */
@Service
public class ShareService {

    private static final Logger log = LoggerFactory.getLogger(ShareService.class);

    static final String EMBEDDING_QUEUE = "embedding:queue";

    private final PostStore postStore;
    private final TimelineStore timelineStore;
    private final CounterStore counterStore;
    private final StringRedisTemplate redisTemplate;
    private final ObjectStorageService objectStorageService;
    private final UserService userService;
    private final EmbeddingProperties embeddingProperties;
    private final ContentModerator moderator;
    private final boolean embeddingQueueEnabled;

    public ShareService(PostStore postStore,
                        TimelineStore timelineStore,
                        CounterStore counterStore,
                        StringRedisTemplate redisTemplate,
                        ObjectStorageService objectStorageService,
                        UserService userService,
                        EmbeddingProperties embeddingProperties,
                        ContentModerator moderator,
                        PersistenceProperties persistenceProperties) {
        this.postStore = postStore;
        this.timelineStore = timelineStore;
        this.counterStore = counterStore;
        this.redisTemplate = redisTemplate;
        this.objectStorageService = objectStorageService;
        this.userService = userService;
        this.embeddingProperties = embeddingProperties;
        this.moderator = moderator;
        this.embeddingQueueEnabled =
            persistenceProperties.getProvider() == PersistenceProperties.Provider.REDIS;
    }

    public Map<String, String> sharePhoto(String user, String content, String url) {
        List<String> urls = url == null ? null : List.of(url);
        return createStatusUpdate(user, content, "photo", urls, null, null, null);
    }

    public Map<String, String> sharePhoto(String user, String content, byte[] bytes) {
        return sharePhoto(user, content, bytes, null);
    }

    public Map<String, String> sharePhoto(String user, String content, byte[] bytes, String contentType) {
        List<String> urls = null;
        String imageHash = null;
        if (bytes != null && bytes.length > 0) {
            ImagePayloads.ImagePayload p = ImagePayloads.fromBytes(bytes, contentType);
            imageHash = Util.getMD5(bytes);
            StoredObject so = objectStorageService.upload(bytes, p.extension(), p.mimeType());
            urls = List.of(so.objectUrl());
        }
        return createStatusUpdate(user, content, "photo", urls, imageHash, null, null);
    }

    public Map<String, String> sharePhotos(String user, String content,
                                           List<byte[]> imageBytes, List<String> contentTypes) {
        if (imageBytes == null || imageBytes.isEmpty()) {
            return createStatusUpdate(user, content, "photo", null, null, null, null);
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
        return createStatusUpdate(user, content, "photo", urls, firstHash, null, null);
    }

    public Map<String, String> shareVideo(String user, String content, String url) {
        return createStatusUpdate(user, content, "video", url == null ? null : List.of(url), null, null, null);
    }

    public Map<String, String> shareAudio(String user, String content, String url) {
        return createStatusUpdate(user, content, "audio", url == null ? null : List.of(url), null, null, null);
    }

    public Map<String, String> shareText(String user, String content) {
        return createStatusUpdate(user, content, "text", null, null, null, null);
    }

    public Map<String, String> replyToPost(String user, String parentPostId, String content) {
        ensurePostExists(parentPostId);
        return createStatusUpdate(user, content, "reply", null, null, parentPostId, null);
    }

    public Map<String, String> resharePost(String user, String postId, String content) {
        ensurePostExists(postId);
        return createStatusUpdate(user, content, "reshare", null, null, null, postId);
    }

    int getMaxImagesPerPost() {
        return embeddingProperties.getMaxImagesPerPost();
    }

    public Map<String, String> reshare(String authenticatedUser, String postId) {
        if (authenticatedUser == null) return null;

        long startTime = System.currentTimeMillis();
        Map<String, Object> postObj = postStore.get(postId).orElse(new LinkedHashMap<>());
        Map<String, String> post = new HashMap<>();
        postObj.forEach((k, v) -> post.put(k, v == null ? null : String.valueOf(v)));

        if (!post.isEmpty()) {
            List<String> keywords = getWords(post.get("content"));
            pushGraph(authenticatedUser, postId, keywords, readImageHash(post));
            post.put("duration", String.valueOf(System.currentTimeMillis() - startTime));
        }
        return post;
    }

    public Map<String, String> getPost(String postId) {
        Map<String, Object> raw = postStore.get(postId)
            .orElseThrow(() -> new PostNotFoundException("Post not found"));
        Map<String, String> post = new HashMap<>();
        raw.forEach((k, v) -> post.put(k, v == null ? null : String.valueOf(v)));
        return post;
    }

    public Map<String, String> updatePost(String user, String postId, String content) {
        Map<String, String> post = getPost(postId);
        userService.ensureAuthor(user, post.get("uid"));

        String updated = Util.unixtime();
        Map<String, String> updates = new LinkedHashMap<>();
        updates.put("content", content);
        updates.put("updated", updated);
        postStore.update(postId, updates);

        post.put("content", content);
        post.put("updated", updated);
        return post;
    }

    public Map<String, String> deletePost(String user, String postId) {
        Map<String, String> post = getPost(postId);
        userService.ensureAuthor(user, post.get("uid"));

        postStore.delete(postId);

        Map<String, String> response = new HashMap<>();
        response.put("deleted", "true");
        response.put("uuid", postId);
        return response;
    }

    private Map<String, String> createStatusUpdate(String authenticatedUser, String content,
                                                   String type, List<String> imageUrls, String imageHash,
                                                   String parentPostId, String sharedPostId) {
        if (authenticatedUser == null) return null;

        if (moderator.enabled() && content != null && !content.isBlank()) {
            ModerationDecision decision = moderator.moderate(content);
            if (decision.blocked()) throw new ContentBlockedException(decision);
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
        if (imageCount > 0)       post.put("imageCount", Integer.toString(imageCount));
        if (imageHash != null)    post.put("imageHash", imageHash);
        if (parentPostId != null) post.put("parentId", parentPostId);
        if (sharedPostId != null) post.put("sharedPostId", sharedPostId);

        List<String> keywords = getWords(content);

        log.info("Creating status update: {}", post);

        // Atomic create: post hash + images + per-user counters in one tx.
        postStore.create(postId, post, imageUrls, authenticatedUser, type);

        // Self-timeline push. Other followers get it in pushGraph below.
        addPostToTimeline(authenticatedUser, postId, authenticatedUser);

        if (parentPostId != null) {
            postStore.addReply(parentPostId, postId);
        }

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

    private static boolean shouldEmitEmbedding(String type, String content, int imageCount) {
        if ("audio".equals(type) || "video".equals(type)) return true;
        boolean hasContent = content != null && !content.isBlank();
        return hasContent || imageCount > 0;
    }

    private void pushGraph(String authenticatedUser, String postId, List<String> keywords, String imageHash) {
        Set<String> followers = userService.followerUids(authenticatedUser);
        for (String followerUid : followers) {
            if (shouldDeliver(followerUid, authenticatedUser, keywords, imageHash)) {
                addPostToTimeline(followerUid, postId, authenticatedUser);
            }
        }
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
        Double ever = getSocialImportance(authorUid);
        double everyoneScore = ever == null ? 0.0 : ever;
        double personalScore = getConnectionZScore(authorUid, recipientUid);
        double fifoTs = System.currentTimeMillis() / 1000.0;
        timelineStore.push(recipientUid, postId, fifoTs, personalScore, everyoneScore);
    }

    private boolean shouldDeliver(String recipientUid, String authorUid, List<String> keywords, String imageHash) {
        if (!userService.canViewContent(recipientUid, authorUid)) return false;
        if (userService.hasMuted(recipientUid, authorUid)) return false;
        if (userService.hasNegativeKeyword(recipientUid, keywords)) return false;
        return !userService.isImageBlocked(recipientUid, imageHash);
    }

    private void ensurePostExists(String postId) {
        if (!postStore.exists(postId)) throw new PostNotFoundException("Post not found");
    }

    private String readImageHash(Map<String, String> post) {
        String imageHash = post.get("imageHash");
        return imageHash != null ? imageHash : post.get("md5");
    }

    private void putIfPresent(Map<String, String> target, String key, String value) {
        if (value != null) target.put(key, value);
    }

    public static List<String> getWords(String text) {
        List<String> words = new ArrayList<>();
        if (text == null) return words;

        BreakIterator breakIterator = BreakIterator.getWordInstance();
        breakIterator.setText(text);
        int lastIndex = breakIterator.first();
        while (BreakIterator.DONE != lastIndex) {
            int firstIndex = lastIndex;
            lastIndex = breakIterator.next();
            if (lastIndex != BreakIterator.DONE && Character.isLetterOrDigit(text.charAt(firstIndex))) {
                String word = text.substring(firstIndex, lastIndex);
                if (!words.contains(word)) words.add(word);
            }
        }
        return words;
    }

    public static List<String> getHashTags(String text) {
        List<String> hashTags = new ArrayList<>();
        getWords(text).stream().filter(s -> s.startsWith("#")).forEach(hashTags::add);
        return hashTags;
    }
}
