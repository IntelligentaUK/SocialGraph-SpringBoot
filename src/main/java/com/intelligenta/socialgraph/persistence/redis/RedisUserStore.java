package com.intelligenta.socialgraph.persistence.redis;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.intelligenta.socialgraph.persistence.UserStore;
import com.intelligenta.socialgraph.util.Util;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "persistence.infinispan", name = "client-mode",
    havingValue = "resp", matchIfMissing = true)
public class RedisUserStore implements UserStore {

    private static final String UID_INDEX = "user:uid";

    private final StringRedisTemplate redis;

    public RedisUserStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private static String userKey(String username) { return "user:" + username; }

    @Override
    public String register(String username, Map<String, String> userHash, String uid,
                           String token, Duration tokenTtl) {
        String activationToken = Util.UUID();
        redis.multi();
        redis.opsForHash().putAll(userKey(username), new LinkedHashMap<>(userHash));
        redis.opsForHash().put(UID_INDEX, uid, username);
        redis.opsForValue().set("user:activations:" + activationToken + ":uid", uid);
        redis.opsForValue().set("tokens:" + token, uid);
        redis.expire("tokens:" + token, tokenTtl);
        redis.opsForHash().increment(userKey(username), "polyCount", 1);
        redis.exec();
        return activationToken;
    }

    @Override
    public Optional<Map<String, String>> find(String username) {
        Map<Object, Object> raw = redis.opsForHash().entries(userKey(username));
        if (raw.isEmpty()) return Optional.empty();
        Map<String, String> typed = new LinkedHashMap<>();
        raw.forEach((k, v) -> typed.put(String.valueOf(k), String.valueOf(v)));
        return Optional.of(typed);
    }

    @Override
    public Optional<String> findUsernameByUid(String uid) {
        Object v = redis.opsForHash().get(UID_INDEX, uid);
        return Optional.ofNullable((String) v);
    }

    @Override
    public Optional<String> findUidByUsername(String username) {
        Object v = redis.opsForHash().get(userKey(username), "uuid");
        return Optional.ofNullable((String) v);
    }

    @Override
    public boolean exists(String username) {
        return Boolean.TRUE.equals(redis.hasKey(userKey(username)));
    }

    @Override
    public boolean uidExists(String uid) {
        return Boolean.TRUE.equals(redis.opsForHash().hasKey(UID_INDEX, uid));
    }

    @Override
    public Optional<String> getField(String username, String field) {
        Object v = redis.opsForHash().get(userKey(username), field);
        return Optional.ofNullable((String) v);
    }

    @Override
    public List<Optional<String>> getFields(String username, List<String> fields) {
        List<Object> raw = redis.opsForHash().multiGet(userKey(username), new ArrayList<>(fields));
        List<Optional<String>> out = new ArrayList<>(raw.size());
        for (Object o : raw) out.add(Optional.ofNullable((String) o));
        return out;
    }

    @Override
    public void putField(String username, String field, String value) {
        redis.opsForHash().put(userKey(username), field, value);
    }

    @Override
    public void putAll(String username, Map<String, String> fields) {
        redis.opsForHash().putAll(userKey(username), new LinkedHashMap<>(fields));
    }

    @Override
    public void incrementField(String username, String field, long delta) {
        redis.opsForHash().increment(userKey(username), field, delta);
    }

    @Override
    public Map<String, String> allUidToUsername() {
        Map<Object, Object> raw = redis.opsForHash().entries(UID_INDEX);
        Map<String, String> out = new LinkedHashMap<>();
        raw.forEach((k, v) -> out.put(String.valueOf(k), String.valueOf(v)));
        return out;
    }

    @Override
    public Optional<String> getPublicRsaKey(String uid) {
        Object v = redis.opsForHash().get("user:" + uid + ":crypto", "publicKey");
        return Optional.ofNullable((String) v);
    }

    @Override
    public Optional<String> consumeActivationToken(String activationToken) {
        String uid = redis.opsForValue().get("user:activations:" + activationToken + ":uid");
        return Optional.ofNullable(uid);
    }
}
