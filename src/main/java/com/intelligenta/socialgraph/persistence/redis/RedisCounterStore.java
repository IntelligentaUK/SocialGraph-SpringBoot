package com.intelligenta.socialgraph.persistence.redis;

import com.intelligenta.socialgraph.persistence.CounterStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "persistence.infinispan", name = "client-mode",
    havingValue = "resp", matchIfMissing = true)
public class RedisCounterStore implements CounterStore {

    private final StringRedisTemplate redis;

    public RedisCounterStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private String bucket(Kind kind) {
        return switch (kind) { case PHOTOS -> "photos"; case VIDEOS -> "videos"; case POSTS -> "posts"; };
    }

    @Override
    public void increment(Kind kind, String uid, long delta) {
        redis.opsForHash().increment(bucket(kind), uid, delta);
    }

    @Override
    public long get(Kind kind, String uid) {
        Object v = redis.opsForHash().get(bucket(kind), uid);
        if (v == null) return 0L;
        try { return Long.parseLong(String.valueOf(v)); }
        catch (NumberFormatException e) { return 0L; }
    }
}
