package com.intelligenta.socialgraph.persistence.infinispan;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.intelligenta.socialgraph.persistence.SessionStore;
import org.infinispan.manager.EmbeddedCacheManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "persistence.infinispan", name = "client-mode", havingValue = "native")
public class InfinispanSessionStore implements SessionStore {

    private final EmbeddedCacheManager manager;

    public InfinispanSessionStore(EmbeddedCacheManager manager) { this.manager = manager; }

    @SuppressWarnings("unchecked")
    private org.infinispan.Cache<String, Map<String, String>> cache() {
        return (org.infinispan.Cache<String, Map<String, String>>)
            (org.infinispan.Cache<?, ?>) manager.getCache("sessions");
    }

    @Override public boolean exists(String sessionId) {
        return cache().containsKey(sessionId);
    }

    @Override public void put(String sessionId, Map<String, String> keyPair) {
        cache().put(sessionId, new LinkedHashMap<>(keyPair));
    }

    @Override public Optional<Map<String, String>> get(String sessionId) {
        return Optional.ofNullable(cache().get(sessionId));
    }
}
