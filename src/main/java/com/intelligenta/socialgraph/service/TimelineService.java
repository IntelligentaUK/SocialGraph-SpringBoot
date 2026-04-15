package com.intelligenta.socialgraph.service;

import com.intelligenta.socialgraph.model.TimelineEntry;
import com.intelligenta.socialgraph.model.TimelineResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for timeline generation.
 */
@Service
public class TimelineService {

    private static final Logger log = LoggerFactory.getLogger(TimelineService.class);

    private final StringRedisTemplate redisTemplate;
    private final UserService userService;

    public TimelineService(StringRedisTemplate redisTemplate, UserService userService) {
        this.redisTemplate = redisTemplate;
        this.userService = userService;
    }

    public enum Importance {
        PERSONAL("personal"),
        EVERYONE("everyone");

        private final String text;

        Importance(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    /**
     * Get timeline using FIFO order.
     */
    public TimelineResponse getFifoTimeline(String authenticatedUser, int index, int count) {
        long startTime = System.currentTimeMillis();
        
        List<String> postIds = redisTemplate.opsForList().range(
            "user:" + authenticatedUser + ":timeline", index, index + count - 1);

        List<TimelineEntry> entries = new ArrayList<>();
        if (postIds != null) {
            for (String postId : postIds) {
                TimelineEntry entry = generatePost(authenticatedUser, postId);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        return new TimelineResponse(entries, entries.size(), duration);
    }

    /**
     * Get timeline sorted by social importance.
     */
    public TimelineResponse getSocialImportanceTimeline(String authenticatedUser, int index, int count, 
                                                         Importance importanceType) {
        long startTime = System.currentTimeMillis();

        String key = "user:" + authenticatedUser + ":timeline:" + importanceType.toString() + ":importance";
        var postIdsWithScores = redisTemplate.opsForZSet().reverseRange(key, index, index + count - 1);

        List<TimelineEntry> entries = new ArrayList<>();
        if (postIdsWithScores != null) {
            for (String postId : postIdsWithScores) {
                TimelineEntry entry = generatePost(authenticatedUser, postId);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        return new TimelineResponse(entries, entries.size(), duration);
    }

    public TimelineEntry getPost(String authenticatedUser, String postId) {
        TimelineEntry entry = generatePost(authenticatedUser, postId);
        if (entry == null) {
            throw new com.intelligenta.socialgraph.exception.PostNotFoundException("Post not found");
        }
        return entry;
    }

    public TimelineResponse getReplies(String authenticatedUser, String postId, int index, int count) {
        long startTime = System.currentTimeMillis();
        List<String> replyIds = redisTemplate.opsForList().range(
            "post:" + postId + ":replies", index, index + count - 1);

        List<TimelineEntry> entries = new ArrayList<>();
        if (replyIds != null) {
            for (String replyId : replyIds) {
                TimelineEntry entry = generatePost(authenticatedUser, replyId);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }

        return new TimelineResponse(entries, entries.size(), System.currentTimeMillis() - startTime);
    }

    private TimelineEntry generatePost(String authenticatedUser, String postId) {
        Map<Object, Object> post = redisTemplate.opsForHash().entries("post:" + postId);
        
        if (post.isEmpty() || post.get("id") == null) {
            return null;
        }

        String postUid = (String) post.get("uid");
        if (postUid != null && !userService.canViewContent(authenticatedUser, postUid)) {
            return null;
        }

        String content = (String) post.get("content");
        if (userService.hasNegativeKeyword(authenticatedUser, ShareService.getWords(content))) {
            return null;
        }

        String imageHash = post.containsKey("imageHash")
            ? (String) post.get("imageHash")
            : (String) post.get("md5");
        if (userService.isImageBlocked(authenticatedUser, imageHash)) {
            return null;
        }

        TimelineEntry entry = new TimelineEntry();
        entry.setUuid((String) post.get("id"));
        entry.setType((String) post.get("type"));
        entry.setContent(content);
        entry.setUrl((String) post.get("url"));
        entry.setCreated((String) post.get("created"));
        entry.setUpdated((String) post.get("updated"));
        entry.setParentUuid((String) post.get("parentId"));
        entry.setSharedPostUuid((String) post.get("sharedPostId"));

        if (postUid != null) {
            String username = userService.getUsername(postUid);
            String fullname = userService.getUserField(postUid, "fullname");
            entry.setActorUid(postUid);
            entry.setActorUsername(username);
            entry.setActorFullname(fullname);
        }

        return entry;
    }
}
