package com.intelligenta.socialgraph.persistence.infinispan;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.intelligenta.socialgraph.persistence.RelationStore;
import org.infinispan.Cache;
import org.infinispan.manager.EmbeddedCacheManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "persistence.infinispan", name = "client-mode", havingValue = "native")
public class InfinispanRelationStore implements RelationStore {

    private final EmbeddedCacheManager manager;

    public InfinispanRelationStore(EmbeddedCacheManager manager) { this.manager = manager; }

    @SuppressWarnings("unchecked")
    private Cache<String, Map<Relation, HashSet<String>>> cache() {
        return (Cache<String, Map<Relation, HashSet<String>>>)
            (Cache<?, ?>) manager.getCache("relations");
    }

    private HashSet<String> readSet(String uid, Relation rel) {
        Map<Relation, HashSet<String>> user = cache().get(uid);
        if (user == null) return new HashSet<>();
        HashSet<String> s = user.get(rel);
        return s == null ? new HashSet<>() : s;
    }

    private void writeSet(String uid, Relation rel, HashSet<String> s) {
        Map<Relation, HashSet<String>> user = cache().get(uid);
        Map<Relation, HashSet<String>> next = user == null ? new LinkedHashMap<>() : new LinkedHashMap<>(user);
        next.put(rel, s);
        cache().put(uid, next);
    }

    @Override public boolean add(String uid, Relation relation, String otherUid) {
        HashSet<String> s = readSet(uid, relation);
        if (s.contains(otherUid)) return false;
        HashSet<String> next = new HashSet<>(s);
        next.add(otherUid);
        writeSet(uid, relation, next);
        return true;
    }

    @Override public boolean remove(String uid, Relation relation, String otherUid) {
        HashSet<String> s = readSet(uid, relation);
        if (!s.contains(otherUid)) return false;
        HashSet<String> next = new HashSet<>(s);
        next.remove(otherUid);
        writeSet(uid, relation, next);
        return true;
    }

    @Override public boolean contains(String uid, Relation relation, String otherUid) {
        return readSet(uid, relation).contains(otherUid);
    }

    @Override public Set<String> members(String uid, Relation relation) {
        return Collections.unmodifiableSet(new HashSet<>(readSet(uid, relation)));
    }
}
