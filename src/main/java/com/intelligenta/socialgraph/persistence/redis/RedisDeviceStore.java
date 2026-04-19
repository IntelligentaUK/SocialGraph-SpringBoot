package com.intelligenta.socialgraph.persistence.redis;

import java.util.Collections;
import java.util.Set;

import com.intelligenta.socialgraph.persistence.DeviceStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "persistence.infinispan", name = "client-mode",
    havingValue = "resp", matchIfMissing = true)
public class RedisDeviceStore implements DeviceStore {

    private final StringRedisTemplate redis;

    public RedisDeviceStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private String key(String username) {
        return "user:" + username + ":devices";
    }

    @Override
    public boolean add(String username, String deviceId) {
        Long added = redis.opsForSet().add(key(username), deviceId);
        return added != null && added == 1;
    }

    @Override
    public boolean remove(String username, String deviceId) {
        Long removed = redis.opsForSet().remove(key(username), deviceId);
        return removed != null && removed == 1;
    }

    @Override
    public boolean contains(String username, String deviceId) {
        return Boolean.TRUE.equals(redis.opsForSet().isMember(key(username), deviceId));
    }

    @Override
    public Set<String> list(String username) {
        Set<String> s = redis.opsForSet().members(key(username));
        return s == null ? Collections.emptySet() : s;
    }
}
