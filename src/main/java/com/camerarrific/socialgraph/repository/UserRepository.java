package com.camerarrific.socialgraph.repository;

import com.camerarrific.socialgraph.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Repository for user-related Redis operations.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class UserRepository {

    private static final String USER_PREFIX = "user:";
    private static final String USER_UID_MAP = "user:uid";
    private static final String TOKENS_PREFIX = "tokens:";

    private final StringRedisTemplate redisTemplate;

    /**
     * Check if a user exists by username.
     */
    public boolean existsByUsername(String username) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(USER_PREFIX + username));
    }

    /**
     * Find a user by username.
     */
    public Optional<User> findByUsername(String username) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(USER_PREFIX + username);
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        
        Map<String, String> stringMap = new HashMap<>();
        entries.forEach((k, v) -> stringMap.put(k.toString(), v != null ? v.toString() : null));
        
        User user = User.fromMap(stringMap);
        user.setUsername(username);
        return Optional.of(user);
    }

    /**
     * Find a username by user ID.
     */
    public Optional<String> findUsernameByUid(String uid) {
        Object username = redisTemplate.opsForHash().get(USER_UID_MAP, uid);
        return Optional.ofNullable(username).map(Object::toString);
    }

    /**
     * Find a user by user ID.
     */
    public Optional<User> findByUid(String uid) {
        return findUsernameByUid(uid)
                .flatMap(this::findByUsername);
    }

    /**
     * Save a new user.
     */
    public User save(User user) {
        String key = USER_PREFIX + user.getUsername();
        
        // Convert user to hash map
        Map<String, String> userMap = user.toMap();
        
        redisTemplate.opsForHash().putAll(key, userMap);
        
        // Map UID to username
        redisTemplate.opsForHash().put(USER_UID_MAP, user.getUid(), user.getUsername());
        
        log.debug("Saved user: {} ({})", user.getUsername(), user.getUid());
        return user;
    }

    /**
     * Update a specific field for a user.
     */
    public void updateField(String username, String field, String value) {
        redisTemplate.opsForHash().put(USER_PREFIX + username, field, value);
    }

    /**
     * Get a specific field for a user.
     */
    public Optional<String> getField(String username, String field) {
        Object value = redisTemplate.opsForHash().get(USER_PREFIX + username, field);
        return Optional.ofNullable(value).map(Object::toString);
    }

    /**
     * Get a specific field for a user by UID.
     */
    public Optional<String> getFieldByUid(String uid, String field) {
        return findUsernameByUid(uid)
                .flatMap(username -> getField(username, field));
    }

    /**
     * Increment a counter field for a user.
     */
    public Long incrementField(String username, String field, long delta) {
        return redisTemplate.opsForHash().increment(USER_PREFIX + username, field, delta);
    }

    /**
     * Add a member to a user's set (followers, following, etc.).
     */
    public Long addToSet(String uid, String setName, String memberUid) {
        String key = USER_PREFIX + uid + ":" + setName;
        return redisTemplate.opsForSet().add(key, memberUid);
    }

    /**
     * Remove a member from a user's set.
     */
    public Long removeFromSet(String uid, String setName, String memberUid) {
        String key = USER_PREFIX + uid + ":" + setName;
        return redisTemplate.opsForSet().remove(key, memberUid);
    }

    /**
     * Check if a member exists in a user's set.
     */
    public boolean isMemberOfSet(String uid, String setName, String memberUid) {
        String key = USER_PREFIX + uid + ":" + setName;
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, memberUid));
    }

    /**
     * Get all members of a user's set.
     */
    public Set<String> getSetMembers(String uid, String setName) {
        String key = USER_PREFIX + uid + ":" + setName;
        Set<String> members = redisTemplate.opsForSet().members(key);
        return members != null ? members : Collections.emptySet();
    }

    /**
     * Get the count of members in a user's set.
     */
    public Long getSetSize(String uid, String setName) {
        String key = USER_PREFIX + uid + ":" + setName;
        return redisTemplate.opsForSet().size(key);
    }

    /**
     * Get user's public RSA key.
     */
    public Optional<String> getPublicRsaKey(String uid) {
        Object key = redisTemplate.opsForHash().get(USER_PREFIX + uid + ":crypto", "publicKey");
        return Optional.ofNullable(key).map(Object::toString);
    }

    /**
     * Store a user's public RSA key.
     */
    public void setPublicRsaKey(String uid, String publicKey) {
        redisTemplate.opsForHash().put(USER_PREFIX + uid + ":crypto", "publicKey", publicKey);
    }

    /**
     * Get user's registered devices.
     */
    public Set<String> getDevices(String uid) {
        String key = USER_PREFIX + uid + ":devices";
        Set<String> devices = redisTemplate.opsForSet().members(key);
        return devices != null ? devices : Collections.emptySet();
    }

    /**
     * Add a device to user's registered devices.
     */
    public Long addDevice(String uid, String deviceId) {
        String key = USER_PREFIX + uid + ":devices";
        return redisTemplate.opsForSet().add(key, deviceId);
    }

    /**
     * Check if a keyword is in user's negative keywords list.
     */
    public boolean hasNegativeKeyword(String uid, String keyword) {
        String key = USER_PREFIX + uid + ":negative:keywords";
        return Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(key, keyword));
    }

    /**
     * Add a negative keyword.
     */
    public boolean addNegativeKeyword(String uid, String keyword) {
        String key = USER_PREFIX + uid + ":negative:keywords";
        return Boolean.TRUE.equals(redisTemplate.opsForHash().putIfAbsent(key, keyword, keyword));
    }

    /**
     * Check if an image MD5 is blocked.
     */
    public boolean isImageBlocked(String uid, String md5) {
        String key = USER_PREFIX + uid + ":images:blocked:md5";
        return Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(key, md5));
    }

    /**
     * Block an image by MD5.
     */
    public boolean blockImage(String uid, String md5) {
        String key = USER_PREFIX + uid + ":images:blocked:md5";
        return Boolean.TRUE.equals(redisTemplate.opsForHash().putIfAbsent(key, md5, md5));
    }
}

