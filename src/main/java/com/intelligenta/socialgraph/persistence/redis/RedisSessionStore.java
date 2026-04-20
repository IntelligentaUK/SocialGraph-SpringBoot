package com.intelligenta.socialgraph.persistence.redis;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.intelligenta.socialgraph.persistence.SessionStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "persistence.infinispan", name = "client-mode",
    havingValue = "resp", matchIfMissing = true)
public class RedisSessionStore implements SessionStore {

    private static final String PREFIX = "session:";
    private static final Duration TTL = Duration.ofDays(1);

    private final StringRedisTemplate redis;

    public RedisSessionStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean exists(String sessionId) {
        return Boolean.TRUE.equals(redis.hasKey(PREFIX + sessionId));
    }

    @Override
    public void put(String sessionId, Map<String, String> keyPair) {
        redis.opsForHash().putAll(PREFIX + sessionId, new HashMap<>(keyPair));
        redis.expire(PREFIX + sessionId, TTL);
    }

    @Override
    public Optional<Map<String, String>> get(String sessionId) {
        Map<Object, Object> raw = redis.opsForHash().entries(PREFIX + sessionId);
        if (raw.isEmpty()) return Optional.empty();
        Map<String, String> typed = new LinkedHashMap<>();
        raw.forEach((k, v) -> typed.put(String.valueOf(k), String.valueOf(v)));
        return Optional.of(typed);
    }
}
