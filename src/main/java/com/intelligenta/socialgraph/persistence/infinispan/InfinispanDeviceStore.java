package com.intelligenta.socialgraph.persistence.infinispan;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.intelligenta.socialgraph.persistence.DeviceStore;
import org.infinispan.manager.EmbeddedCacheManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "persistence.infinispan", name = "client-mode", havingValue = "native")
public class InfinispanDeviceStore implements DeviceStore {

    private final EmbeddedCacheManager manager;

    public InfinispanDeviceStore(EmbeddedCacheManager manager) { this.manager = manager; }

    @SuppressWarnings("unchecked")
    private org.infinispan.Cache<String, HashSet<String>> cache() {
        return (org.infinispan.Cache<String, HashSet<String>>)
            (org.infinispan.Cache<?, ?>) manager.getCache("devices");
    }

    @Override public boolean add(String username, String deviceId) {
        HashSet<String> existing = cache().getOrDefault(username, new HashSet<>());
        if (existing.contains(deviceId)) return false;
        HashSet<String> next = new HashSet<>(existing);
        next.add(deviceId);
        cache().put(username, next);
        return true;
    }

    @Override public boolean remove(String username, String deviceId) {
        HashSet<String> existing = cache().get(username);
        if (existing == null || !existing.contains(deviceId)) return false;
        HashSet<String> next = new HashSet<>(existing);
        next.remove(deviceId);
        cache().put(username, next);
        return true;
    }

    @Override public boolean contains(String username, String deviceId) {
        HashSet<String> s = cache().get(username);
        return s != null && s.contains(deviceId);
    }

    @Override public Set<String> list(String username) {
        HashSet<String> s = cache().get(username);
        return s == null ? Collections.emptySet() : new HashSet<>(s);
    }
}
