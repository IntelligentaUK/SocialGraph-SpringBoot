package com.intelligenta.socialgraph.persistence.redis;

import java.util.Collections;
import java.util.Set;

import com.intelligenta.socialgraph.persistence.RelationStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "persistence.infinispan", name = "client-mode",
    havingValue = "resp", matchIfMissing = true)
public class RedisRelationStore implements RelationStore {

    private final StringRedisTemplate redis;

    public RedisRelationStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private String key(String uid, Relation rel) {
        return "user:" + uid + ":" + switch (rel) {
            case FOLLOWERS -> "followers";
            case FOLLOWING -> "following";
            case BLOCKED   -> "blocked";
            case BLOCKERS  -> "blockers";
            case MUTED     -> "muted";
            case MUTERS    -> "muters";
        };
    }

    @Override
    public boolean add(String uid, Relation relation, String otherUid) {
        Long added = redis.opsForSet().add(key(uid, relation), otherUid);
        return added != null && added == 1;
    }

    @Override
    public boolean remove(String uid, Relation relation, String otherUid) {
        Long removed = redis.opsForSet().remove(key(uid, relation), otherUid);
        return removed != null && removed == 1;
    }

    @Override
    public boolean contains(String uid, Relation relation, String otherUid) {
        return Boolean.TRUE.equals(redis.opsForSet().isMember(key(uid, relation), otherUid));
    }

    @Override
    public Set<String> members(String uid, Relation relation) {
        Set<String> s = redis.opsForSet().members(key(uid, relation));
        return s == null ? Collections.emptySet() : s;
    }
}
