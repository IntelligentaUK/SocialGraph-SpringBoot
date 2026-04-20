package com.intelligenta.socialgraph.persistence.redis;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.intelligenta.socialgraph.persistence.CounterStore;
import com.intelligenta.socialgraph.persistence.PostStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis implementation of {@link PostStore}. Reproduces the existing
 * ShareService MULTI/EXEC atomicity: post hash + image list + per-user
 * counters land in one transaction. The counter increment is delegated to
 * {@link CounterStore} but also participates in the MULTI/EXEC — see
 * {@link #create}.
 */
@Component
@ConditionalOnProperty(prefix = "persistence.infinispan", name = "client-mode",
    havingValue = "resp", matchIfMissing = true)
public class RedisPostStore implements PostStore {

    private final StringRedisTemplate redis;

    public RedisPostStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private static String postKey(String postId)    { return "post:" + postId; }
    private static String imagesKey(String postId)  { return "post:" + postId + ":images"; }
    private static String repliesKey(String postId) { return "post:" + postId + ":replies"; }

    @Override
    public String create(String postId, Map<String, String> postFields, List<String> imageUrls,
                         String authorUid, String type) {
        redis.multi();
        redis.opsForHash().putAll(postKey(postId), new LinkedHashMap<>(postFields));
        if (imageUrls != null && !imageUrls.isEmpty()) {
            redis.opsForList().rightPushAll(imagesKey(postId), imageUrls.toArray(new String[0]));
        }
        if ("photo".equals(type)) {
            redis.opsForHash().increment("photos", authorUid, 1);
        } else if ("video".equals(type)) {
            redis.opsForHash().increment("videos", authorUid, 1);
        } else if ("text".equals(type) || "reply".equals(type) || "reshare".equals(type)) {
            redis.opsForHash().increment("posts", authorUid, 1);
        }
        redis.exec();
        return postId;
    }

    @Override
    public Optional<Map<String, Object>> get(String postId) {
        Map<Object, Object> raw = redis.opsForHash().entries(postKey(postId));
        if (raw.isEmpty()) return Optional.empty();
        Map<String, Object> typed = new LinkedHashMap<>();
        raw.forEach((k, v) -> typed.put(String.valueOf(k), v));
        return Optional.of(typed);
    }

    @Override
    public boolean exists(String postId) {
        return Boolean.TRUE.equals(redis.hasKey(postKey(postId)));
    }

    @Override
    public void update(String postId, Map<String, String> updates) {
        redis.opsForHash().putAll(postKey(postId), new LinkedHashMap<>(updates));
    }

    @Override
    public void delete(String postId) {
        redis.delete(postKey(postId));
    }

    @Override
    public void addReply(String parentPostId, String replyPostId) {
        redis.opsForList().leftPush(repliesKey(parentPostId), replyPostId);
    }

    @Override
    public List<String> replies(String postId, int offset, int limit) {
        List<String> out = redis.opsForList().range(repliesKey(postId), offset, offset + limit - 1);
        return out == null ? Collections.emptyList() : out;
    }

    @Override
    public List<String> images(String postId) {
        List<String> out = redis.opsForList().range(imagesKey(postId), 0, -1);
        return out == null ? Collections.emptyList() : out;
    }
}
