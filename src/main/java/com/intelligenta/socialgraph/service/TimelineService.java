package com.intelligenta.socialgraph.service;

import com.intelligenta.socialgraph.model.TimelineEntry;
import com.intelligenta.socialgraph.model.TimelineResponse;
import com.intelligenta.socialgraph.persistence.PostStore;
import com.intelligenta.socialgraph.persistence.TimelineStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Timeline generation backed by {@link TimelineStore} + {@link PostStore}. */
@Service
public class TimelineService {

    private static final Logger log = LoggerFactory.getLogger(TimelineService.class);

    private final TimelineStore timelines;
    private final PostStore posts;
    private final UserService userService;

    public TimelineService(TimelineStore timelines, PostStore posts, UserService userService) {
        this.timelines = timelines;
        this.posts = posts;
        this.userService = userService;
    }

    public enum Importance {
        PERSONAL("personal"),
        EVERYONE("everyone");

        private final String text;
        Importance(String text) { this.text = text; }
        @Override public String toString() { return text; }

        TimelineStore.Kind toKind() {
            return this == PERSONAL ? TimelineStore.Kind.PERSONAL_IMPORTANCE : TimelineStore.Kind.EVERYONE_IMPORTANCE;
        }
    }

    public TimelineResponse getFifoTimeline(String authenticatedUser, int index, int count) {
        long startTime = System.currentTimeMillis();
        List<String> postIds = timelines.range(authenticatedUser, TimelineStore.Kind.FIFO, index, count);

        List<TimelineEntry> entries = new ArrayList<>();
        for (String postId : postIds) {
            TimelineEntry entry = generatePost(authenticatedUser, postId);
            if (entry != null) entries.add(entry);
        }
        return new TimelineResponse(entries, entries.size(), System.currentTimeMillis() - startTime);
    }

    public TimelineResponse getSocialImportanceTimeline(String authenticatedUser, int index, int count,
                                                         Importance importanceType) {
        long startTime = System.currentTimeMillis();
        List<String> postIds = timelines.range(authenticatedUser, importanceType.toKind(), index, count);

        List<TimelineEntry> entries = new ArrayList<>();
        for (String postId : postIds) {
            TimelineEntry entry = generatePost(authenticatedUser, postId);
            if (entry != null) entries.add(entry);
        }
        return new TimelineResponse(entries, entries.size(), System.currentTimeMillis() - startTime);
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
        List<String> replyIds = posts.replies(postId, index, count);

        List<TimelineEntry> entries = new ArrayList<>();
        for (String replyId : replyIds) {
            TimelineEntry entry = generatePost(authenticatedUser, replyId);
            if (entry != null) entries.add(entry);
        }
        return new TimelineResponse(entries, entries.size(), System.currentTimeMillis() - startTime);
    }

    private TimelineEntry generatePost(String authenticatedUser, String postId) {
        Map<String, Object> post = posts.get(postId).orElse(null);
        if (post == null || post.get("id") == null) return null;

        String postUid = (String) post.get("uid");
        if (postUid != null && !userService.canViewContent(authenticatedUser, postUid)) return null;

        String content = (String) post.get("content");
        if (userService.hasNegativeKeyword(authenticatedUser, ShareService.getWords(content))) return null;

        String imageHash = post.containsKey("imageHash")
            ? (String) post.get("imageHash") : (String) post.get("md5");
        if (userService.isImageBlocked(authenticatedUser, imageHash)) return null;

        TimelineEntry entry = new TimelineEntry();
        entry.setUuid((String) post.get("id"));
        entry.setType((String) post.get("type"));
        entry.setContent(content);
        entry.setUrl((String) post.get("url"));
        entry.setCreated((String) post.get("created"));
        entry.setUpdated((String) post.get("updated"));
        entry.setParentUuid((String) post.get("parentId"));
        entry.setSharedPostUuid((String) post.get("sharedPostId"));

        String imageCountStr = (String) post.get("imageCount");
        if (imageCountStr != null) {
            int c = Integer.parseInt(imageCountStr);
            if (c > 0) {
                List<String> urls = posts.images(entry.getUuid());
                if (!urls.isEmpty()) entry.setImageUrls(urls);
            }
        } else if (entry.getUrl() != null) {
            entry.setImageUrls(List.of(entry.getUrl()));
        }

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
