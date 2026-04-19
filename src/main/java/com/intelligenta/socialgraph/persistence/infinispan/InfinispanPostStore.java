package com.intelligenta.socialgraph.persistence.infinispan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.intelligenta.socialgraph.persistence.CounterStore;
import com.intelligenta.socialgraph.persistence.PostStore;
import org.infinispan.Cache;
import org.infinispan.manager.EmbeddedCacheManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "persistence.infinispan", name = "client-mode", havingValue = "native")
public class InfinispanPostStore implements PostStore {

    private final EmbeddedCacheManager manager;
    private final CounterStore counters;

    public InfinispanPostStore(EmbeddedCacheManager manager, CounterStore counters) {
        this.manager = manager;
        this.counters = counters;
    }

    @SuppressWarnings("unchecked")
    private Cache<String, Map<String, String>> posts() {
        return (Cache<String, Map<String, String>>) (Cache<?, ?>) manager.getCache("posts");
    }

    @SuppressWarnings("unchecked")
    private Cache<String, ArrayList<String>> replies() {
        return (Cache<String, ArrayList<String>>) (Cache<?, ?>) manager.getCache("post-replies");
    }

    @SuppressWarnings("unchecked")
    private Cache<String, ArrayList<String>> images() {
        return (Cache<String, ArrayList<String>>) (Cache<?, ?>) manager.getCache("post-images");
    }

    @Override
    public String create(String postId, Map<String, String> postFields, List<String> imageUrls,
                         String authorUid, String type) {
        posts().put(postId, new LinkedHashMap<>(postFields));
        if (imageUrls != null && !imageUrls.isEmpty()) {
            images().put(postId, new ArrayList<>(imageUrls));
        }
        switch (type) {
            case "photo"   -> counters.increment(CounterStore.Kind.PHOTOS, authorUid, 1);
            case "video"   -> counters.increment(CounterStore.Kind.VIDEOS, authorUid, 1);
            case "text", "reply", "reshare" -> counters.increment(CounterStore.Kind.POSTS, authorUid, 1);
            default -> {}
        }
        return postId;
    }

    @Override public Optional<Map<String, Object>> get(String postId) {
        Map<String, String> v = posts().get(postId);
        if (v == null) return Optional.empty();
        Map<String, Object> out = new LinkedHashMap<>();
        out.putAll(v);
        return Optional.of(out);
    }

    @Override public boolean exists(String postId) { return posts().containsKey(postId); }

    @Override public void update(String postId, Map<String, String> updates) {
        Map<String, String> existing = posts().get(postId);
        Map<String, String> next = existing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(existing);
        next.putAll(updates);
        posts().put(postId, next);
    }

    @Override public void delete(String postId) { posts().remove(postId); }

    @Override public void addReply(String parentPostId, String replyPostId) {
        ArrayList<String> list = replies().getOrDefault(parentPostId, new ArrayList<>());
        ArrayList<String> next = new ArrayList<>(list);
        next.add(0, replyPostId);
        replies().put(parentPostId, next);
    }

    @Override public List<String> replies(String postId, int offset, int limit) {
        ArrayList<String> list = replies().get(postId);
        if (list == null || list.isEmpty()) return Collections.emptyList();
        int from = Math.max(0, offset);
        int to = Math.min(list.size(), offset + limit);
        return from >= list.size() ? Collections.emptyList() : new ArrayList<>(list.subList(from, to));
    }

    @Override public List<String> images(String postId) {
        ArrayList<String> list = images().get(postId);
        return list == null ? Collections.emptyList() : new ArrayList<>(list);
    }
}
