package com.intelligenta.socialgraph.persistence.infinispan;

import java.util.LinkedHashMap;
import java.util.Map;

import com.intelligenta.socialgraph.persistence.CounterStore;
import org.infinispan.Cache;
import org.infinispan.manager.EmbeddedCacheManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "persistence.infinispan", name = "client-mode", havingValue = "native")
public class InfinispanCounterStore implements CounterStore {

    private final EmbeddedCacheManager manager;

    public InfinispanCounterStore(EmbeddedCacheManager manager) { this.manager = manager; }

    @SuppressWarnings("unchecked")
    private Cache<String, Map<String, Long>> cache() {
        return (Cache<String, Map<String, Long>>) (Cache<?, ?>) manager.getCache("counters");
    }

    private String bucket(Kind kind) {
        return switch (kind) { case PHOTOS -> "photos"; case VIDEOS -> "videos"; case POSTS -> "posts"; };
    }

    @Override public void increment(Kind kind, String uid, long delta) {
        String b = bucket(kind);
        Map<String, Long> existing = cache().get(b);
        Map<String, Long> next = existing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(existing);
        next.merge(uid, delta, Long::sum);
        cache().put(b, next);
    }

    @Override public long get(Kind kind, String uid) {
        Map<String, Long> v = cache().get(bucket(kind));
        return v == null ? 0L : v.getOrDefault(uid, 0L);
    }
}
