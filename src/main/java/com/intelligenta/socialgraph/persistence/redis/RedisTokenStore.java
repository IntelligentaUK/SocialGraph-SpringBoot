package com.intelligenta.socialgraph.persistence.redis;

import java.time.Duration;
import java.util.Optional;

import com.intelligenta.socialgraph.persistence.TokenStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "persistence.infinispan", name = "client-mode",
    havingValue = "resp", matchIfMissing = true)
public class RedisTokenStore implements TokenStore {

    private static final String PREFIX = "tokens:";

    private final StringRedisTemplate redis;

    public RedisTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void issue(String token, String uid, Duration ttl) {
        redis.opsForValue().set(PREFIX + token, uid, ttl);
    }

    @Override
    public Optional<String> resolve(String token) {
        return Optional.ofNullable(redis.opsForValue().get(PREFIX + token));
    }

    @Override
    public void revoke(String token) {
        redis.delete(PREFIX + token);
    }
}
