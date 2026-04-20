package com.intelligenta.socialgraph.persistence.infinispan;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.intelligenta.socialgraph.persistence.ContentFilterStore;
import org.infinispan.manager.EmbeddedCacheManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "persistence.infinispan", name = "client-mode", havingValue = "native")
public class InfinispanContentFilterStore implements ContentFilterStore {

    private final EmbeddedCacheManager manager;

    public InfinispanContentFilterStore(EmbeddedCacheManager manager) { this.manager = manager; }

    @SuppressWarnings("unchecked")
    private org.infinispan.Cache<String, Map<String, HashSet<String>>> cache() {
        return (org.infinispan.Cache<String, Map<String, HashSet<String>>>)
            (org.infinispan.Cache<?, ?>) manager.getCache("content-filters");
    }

    private HashSet<String> readSet(String uid, String kind) {
        Map<String, HashSet<String>> user = cache().get(uid);
        if (user == null) return new HashSet<>();
        HashSet<String> s = user.get(kind);
        return s == null ? new HashSet<>() : s;
    }

    private void writeSet(String uid, String kind, HashSet<String> s) {
        Map<String, HashSet<String>> user = cache().get(uid);
        Map<String, HashSet<String>> next = user == null ? new LinkedHashMap<>() : new LinkedHashMap<>(user);
        next.put(kind, s);
        cache().put(uid, next);
    }

    @Override public void addNegativeKeyword(String uid, String keyword) {
        HashSet<String> s = readSet(uid, "keywords");
        s.add(keyword);
        writeSet(uid, "keywords", s);
    }

    @Override public boolean hasAnyNegativeKeyword(String uid, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) return false;
        HashSet<String> s = readSet(uid, "keywords");
        for (String k : keywords) if (s.contains(k)) return true;
        return false;
    }

    @Override public void blockImage(String uid, String md5) {
        HashSet<String> s = readSet(uid, "images");
        s.add(md5);
        writeSet(uid, "images", s);
    }

    @Override public boolean isImageBlocked(String uid, String md5) {
        if (md5 == null || md5.isBlank()) return false;
        return readSet(uid, "images").contains(md5);
    }
}
