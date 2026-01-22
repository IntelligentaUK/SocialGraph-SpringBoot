package com.camerarrific.socialgraph.repository;

import com.camerarrific.socialgraph.model.Post;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * Repository for post-related Redis operations.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class PostRepository {

    private static final String POST_PREFIX = "post:";
    private static final String USER_PREFIX = "user:";

    private final StringRedisTemplate redisTemplate;

    /**
     * Check if a post exists.
     */
    public boolean exists(String postId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(POST_PREFIX + postId));
    }

    /**
     * Find a post by ID.
     */
    public Optional<Post> findById(String postId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(POST_PREFIX + postId);
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        
        Map<String, String> stringMap = new HashMap<>();
        entries.forEach((k, v) -> stringMap.put(k.toString(), v != null ? v.toString() : null));
        
        return Optional.of(Post.fromMap(stringMap));
    }

    /**
     * Save a new post.
     */
    public Post save(Post post) {
        String key = POST_PREFIX + post.getId();
        
        // Save post hash
        redisTemplate.opsForHash().putAll(key, post.toMap());
        
        // Add to user's timeline
        String timelineKey = USER_PREFIX + post.getUid() + ":timeline";
        redisTemplate.opsForList().leftPush(timelineKey, post.getId());
        
        // Increment photo/video counter
        if ("photo".equals(post.getType())) {
            redisTemplate.opsForHash().increment("photos", post.getUid(), 1);
        } else if ("video".equals(post.getType())) {
            redisTemplate.opsForHash().increment("videos", post.getUid(), 1);
        }
        
        log.debug("Saved post: {} by user {}", post.getId(), post.getUid());
        return post;
    }

    /**
     * Get posts from a user's timeline.
     */
    public List<String> getTimelinePosts(String uid, int start, int count) {
        String key = USER_PREFIX + uid + ":timeline";
        List<String> posts = redisTemplate.opsForList().range(key, start, start + count - 1);
        return posts != null ? posts : Collections.emptyList();
    }

    /**
     * Push a post to a user's timeline.
     */
    public Long pushToTimeline(String uid, String postId) {
        String key = USER_PREFIX + uid + ":timeline";
        return redisTemplate.opsForList().leftPush(key, postId);
    }

    /**
     * Remove a post from a user's timeline.
     */
    public Long removeFromTimeline(String uid, String postId) {
        String key = USER_PREFIX + uid + ":timeline";
        return redisTemplate.opsForList().remove(key, 1, postId);
    }

    /**
     * Get posts sorted by social importance.
     */
    public Set<String> getTimelineByImportance(String uid, String importanceType, long start, long count) {
        String key = USER_PREFIX + uid + ":timeline:" + importanceType + ":importance";
        Set<String> posts = redisTemplate.opsForZSet().range(key, start, start + count - 1);
        return posts != null ? posts : Collections.emptySet();
    }

    /**
     * Add post to importance sorted set.
     */
    public Boolean addToImportanceSet(String uid, String importanceType, String postId, double score) {
        String key = USER_PREFIX + uid + ":timeline:" + importanceType + ":importance";
        return redisTemplate.opsForZSet().add(key, postId, score);
    }

    /**
     * Add an action (like, love, etc.) to a post.
     */
    public Long addAction(String postId, String actionKey, String actorUid) {
        String key = POST_PREFIX + postId + actionKey;
        return redisTemplate.opsForList().leftPush(key, actorUid);
    }

    /**
     * Remove an action from a post.
     */
    public Long removeAction(String postId, String actionKey, String actorUid) {
        String key = POST_PREFIX + postId + actionKey;
        return redisTemplate.opsForList().remove(key, 0, actorUid);
    }

    /**
     * Get actors who performed an action on a post.
     */
    public List<String> getActionActors(String postId, String actionKey, int start, int count) {
        String key = POST_PREFIX + postId + actionKey;
        List<String> actors = redisTemplate.opsForList().range(key, start, start + count - 1);
        return actors != null ? actors : Collections.emptyList();
    }

    /**
     * Check if a user has performed an action on a post.
     */
    public boolean hasAction(String postId, String actorUid, String actionNoun) {
        String key = POST_PREFIX + postId + ":" + actorUid + ":";
        Object value = redisTemplate.opsForHash().get(key, actionNoun);
        return value != null;
    }

    /**
     * Mark that a user has performed an action on a post.
     */
    public Boolean setActionFlag(String postId, String actorUid, String actionNoun) {
        String key = POST_PREFIX + postId + ":" + actorUid + ":";
        return redisTemplate.opsForHash().putIfAbsent(key, actionNoun, "1");
    }

    /**
     * Remove the action flag.
     */
    public Long removeActionFlag(String postId, String actorUid, String actionNoun) {
        String key = POST_PREFIX + postId + ":" + actorUid + ":";
        return redisTemplate.opsForHash().delete(key, actionNoun);
    }
}

