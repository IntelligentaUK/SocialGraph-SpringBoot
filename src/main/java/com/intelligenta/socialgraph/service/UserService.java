package com.intelligenta.socialgraph.service;

import com.intelligenta.socialgraph.PasswordHash;
import com.intelligenta.socialgraph.config.AppProperties;
import com.intelligenta.socialgraph.exception.AlreadyFollowingException;
import com.intelligenta.socialgraph.exception.AlreadyRegisteredException;
import com.intelligenta.socialgraph.exception.CannotFollowSelfException;
import com.intelligenta.socialgraph.exception.InvalidCredentialsException;
import com.intelligenta.socialgraph.exception.NotFollowingException;
import com.intelligenta.socialgraph.exception.UserNotFoundException;
import com.intelligenta.socialgraph.model.AuthResponse;
import com.intelligenta.socialgraph.model.MemberInfo;
import com.intelligenta.socialgraph.util.Util;
import org.springframework.security.access.AccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for user-related operations.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final StringRedisTemplate redisTemplate;
    private final AppProperties appProperties;

    public UserService(StringRedisTemplate redisTemplate, AppProperties appProperties) {
        this.redisTemplate = redisTemplate;
        this.appProperties = appProperties;
    }

    /**
     * Register a new user.
     */
    public AuthResponse register(String username, String password, String email) throws NoSuchAlgorithmException {
        // Check if user already exists
        if (Boolean.TRUE.equals(redisTemplate.hasKey("user:" + username))) {
            throw new AlreadyRegisteredException("Username already registered");
        }

        String salt = PasswordHash.createSalt();
        String hash = PasswordHash.createArgon2Hash(salt + password);
        String uid = Util.UUID();
        String poly = Util.UUID();
        String token = Util.UUID();

        Map<String, String> userHash = new HashMap<>();
        userHash.put("passwordHash", hash);
        userHash.put("uuid", uid);
        userHash.put("username", username);
        userHash.put("email", email);
        userHash.put("salt", salt);
        userHash.put("poly", poly);
        userHash.put("created", Util.unixtime());
        userHash.put("followers", "0");
        userHash.put("following", "0");

        long tokenExpiration = appProperties.getSecurity().getTokenExpirationSeconds();
        String activationToken = Util.UUID();

        // Execute Redis transaction
        redisTemplate.multi();
        redisTemplate.opsForHash().putAll("user:" + username, userHash);
        redisTemplate.opsForHash().put("user:uid", uid, username);
        redisTemplate.opsForValue().set("user:activations:" + activationToken + ":uid", uid);
        redisTemplate.opsForValue().set("tokens:" + token, uid);
        redisTemplate.expire("tokens:" + token, java.time.Duration.ofSeconds(tokenExpiration));
        redisTemplate.opsForHash().increment("user:" + username, "polyCount", 1);
        redisTemplate.exec();

        AuthResponse response = new AuthResponse(username, token, uid, tokenExpiration);
        response.setActivationToken(activationToken);
        response.setFollowers("0");
        response.setFollowing("0");
        return response;
    }

    /**
     * Login a user.
     */
    public AuthResponse login(String username, String password) {
        List<Object> fields = redisTemplate.opsForHash().multiGet("user:" + username, 
            List.of("passwordHash", "salt", "poly"));

        if (fields.isEmpty() || fields.get(0) == null) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        String passwordHash = (String) fields.get(0);
        String salt = (String) fields.get(1);

        if (!PasswordHash.validateArgon2Hash(salt + password, passwordHash)) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        String token = Util.UUID();
        List<Object> userFields = redisTemplate.opsForHash().multiGet("user:" + username,
            List.of("uuid", "followers", "following"));

        String uid = (String) userFields.get(0);
        String followers = (String) userFields.get(1);
        String following = (String) userFields.get(2);

        long tokenExpiration = appProperties.getSecurity().getTokenExpirationSeconds();

        redisTemplate.opsForValue().set("tokens:" + token, uid);
        redisTemplate.expire("tokens:" + token, java.time.Duration.ofSeconds(tokenExpiration));

        AuthResponse response = new AuthResponse(username, token, uid, tokenExpiration);
        response.setFollowers(followers);
        response.setFollowing(following);
        return response;
    }

    /**
     * Get authenticated user UID from token.
     */
    public String authenticatedUser(String token) {
        return redisTemplate.opsForValue().get("tokens:" + token);
    }

    /**
     * Get username by UID.
     */
    public String getUsername(String uid) {
        return (String) redisTemplate.opsForHash().get("user:uid", uid);
    }

    /**
     * Get UID by username.
     */
    public String getUid(String username) {
        return (String) redisTemplate.opsForHash().get("user:" + username, "uuid");
    }

    /**
     * Check if user exists.
     */
    public boolean userExists(String username) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("user:" + username));
    }

    /**
     * Check if UID exists.
     */
    public boolean uidExists(String uid) {
        return Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey("user:uid", uid));
    }

    /**
     * Follow a user.
     */
    public void follow(String authenticatedUid, String targetUid, String targetUsername) {
        // Validate target user exists
        targetUid = resolveTargetUid(targetUid, targetUsername);
        
        if (targetUid == null || !uidExists(targetUid)) {
            throw new UserNotFoundException("User not found");
        }

        if (authenticatedUid.equals(targetUid)) {
            throw new CannotFollowSelfException("Cannot follow yourself");
        }

        Long added = redisTemplate.opsForSet().add("user:" + targetUid + ":followers", authenticatedUid);
        if (added == null || added == 0) {
            throw new AlreadyFollowingException("Already following this user");
        }

        // Update following set and counters
        redisTemplate.opsForSet().add("user:" + authenticatedUid + ":following", targetUid);
        incrementCounterForUid(targetUid, "followers", 1);
        incrementCounterForUid(authenticatedUid, "following", 1);
    }

    /**
     * Unfollow a user.
     */
    public void unfollow(String authenticatedUid, String targetUid) {
        if (authenticatedUid.equals(targetUid)) {
            throw new CannotFollowSelfException("Cannot unfollow yourself");
        }

        Long removed = redisTemplate.opsForSet().remove("user:" + targetUid + ":followers", authenticatedUid);
        if (removed == null || removed == 0) {
            throw new NotFollowingException("Not following this user");
        }

        // Update following set and counters
        redisTemplate.opsForSet().remove("user:" + authenticatedUid + ":following", targetUid);
        incrementCounterForUid(targetUid, "followers", -1);
        incrementCounterForUid(authenticatedUid, "following", -1);
    }

    /**
     * Get members of a set (followers, following, friends, blocked, etc.)
     */
    public List<MemberInfo> getMembers(String uid, String setType) {
        long startTime = System.currentTimeMillis();
        Set<String> memberUids = "friends".equals(setType)
            ? getFriends(uid)
            : redisTemplate.opsForSet().members("user:" + uid + ":" + setType);

        List<MemberInfo> members = new ArrayList<>();
        if (memberUids != null) {
            for (String memberUid : memberUids) {
                String username = getUsername(memberUid);
                String fullname = (String) redisTemplate.opsForHash().get("user:" + username, "fullname");
                members.add(new MemberInfo(memberUid, username, fullname));
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.debug("getMembers({}, {}) took {}ms", uid, setType, duration);

        return members;
    }

    /**
     * Get user's public RSA key.
     */
    public String getPublicRSAKey(String uid) {
        return (String) redisTemplate.opsForHash().get("user:" + uid + ":crypto", "publicKey");
    }

    /**
     * Get a user field value.
     */
    public String getUserField(String uid, String field) {
        String username = getUsername(uid);
        if (username != null) {
            return (String) redisTemplate.opsForHash().get("user:" + username, field);
        }
        return null;
    }

    /**
     * Activate user account.
     */
    public Map<String, String> activateAccount(String activationToken) {
        String uid = redisTemplate.opsForValue().get("user:activations:" + activationToken + ":uid");
        String username = uid != null ? getUsername(uid) : null;

        Map<String, String> result = new HashMap<>();
        if (username != null) {
            result.put("username", username);
            result.put("uid", uid);
            result.put("activated", "true");
            redisTemplate.opsForHash().put("user:" + username, "activated", "true");
        } else {
            result.put("activated", "false");
        }
        return result;
    }

    public Map<String, Object> getProfile(String targetUid, String viewerUid) {
        String username = getUsername(targetUid);
        if (username == null) {
            throw new UserNotFoundException("User not found");
        }

        Map<String, Object> profile = new HashMap<>();
        profile.put("uid", targetUid);
        profile.put("username", username);
        profile.put("fullname", getUserField(targetUid, "fullname"));
        profile.put("bio", getUserField(targetUid, "bio"));
        profile.put("profilePicture", getUserField(targetUid, "profilePicture"));
        profile.put("created", getUserField(targetUid, "created"));
        profile.put("followers", String.valueOf(getCounter(targetUid, "followers")));
        profile.put("following", String.valueOf(getCounter(targetUid, "following")));
        profile.put("isBlocked", viewerUid != null && hasBlocked(viewerUid, targetUid));
        profile.put("isMuted", viewerUid != null && hasMuted(viewerUid, targetUid));
        profile.put("blocksViewer", viewerUid != null && hasBlocked(targetUid, viewerUid));
        return profile;
    }

    public Map<String, Object> updateProfile(String uid, String fullname, String bio, String profilePicture) {
        String username = requireUsername(uid);
        Map<String, String> updates = new HashMap<>();

        if (fullname != null) {
            updates.put("fullname", fullname);
        }
        if (bio != null) {
            updates.put("bio", bio);
        }
        if (profilePicture != null) {
            updates.put("profilePicture", profilePicture);
        }

        if (!updates.isEmpty()) {
            redisTemplate.opsForHash().putAll("user:" + username, updates);
        }

        return getProfile(uid, uid);
    }

    public List<MemberInfo> searchUsers(String query, int index, int count) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase();
        List<MemberInfo> matches = redisTemplate.opsForHash().entries("user:uid").entrySet().stream()
            .map(entry -> new MemberInfo((String) entry.getKey(), (String) entry.getValue(), null))
            .filter(member -> normalizedQuery.isEmpty()
                || member.getUsername().toLowerCase().contains(normalizedQuery)
                || String.valueOf(getUserField(member.getUid(), "fullname")).toLowerCase().contains(normalizedQuery))
            .sorted((left, right) -> left.getUsername().compareToIgnoreCase(right.getUsername()))
            .collect(Collectors.toList());

        int fromIndex = Math.max(0, index);
        int toIndex = Math.min(matches.size(), fromIndex + Math.max(count, 0));
        if (fromIndex >= matches.size()) {
            return new ArrayList<>();
        }

        List<MemberInfo> page = new ArrayList<>();
        for (MemberInfo member : matches.subList(fromIndex, toIndex)) {
            page.add(new MemberInfo(member.getUid(), member.getUsername(), getUserField(member.getUid(), "fullname")));
        }
        return page;
    }

    public boolean block(String authenticatedUid, String targetUid) {
        targetUid = resolveTargetUid(targetUid, null);
        validateDistinctKnownUsers(authenticatedUid, targetUid);

        Long added = redisTemplate.opsForSet().add("user:" + authenticatedUid + ":blocked", targetUid);
        redisTemplate.opsForSet().add("user:" + targetUid + ":blockers", authenticatedUid);
        removeFollowIfPresent(authenticatedUid, targetUid);
        removeFollowIfPresent(targetUid, authenticatedUid);
        return added != null && added == 1;
    }

    public boolean unblock(String authenticatedUid, String targetUid) {
        targetUid = resolveTargetUid(targetUid, null);
        validateDistinctKnownUsers(authenticatedUid, targetUid);

        Long removed = redisTemplate.opsForSet().remove("user:" + authenticatedUid + ":blocked", targetUid);
        redisTemplate.opsForSet().remove("user:" + targetUid + ":blockers", authenticatedUid);
        return removed != null && removed == 1;
    }

    public boolean mute(String authenticatedUid, String targetUid) {
        targetUid = resolveTargetUid(targetUid, null);
        validateDistinctKnownUsers(authenticatedUid, targetUid);

        Long added = redisTemplate.opsForSet().add("user:" + authenticatedUid + ":muted", targetUid);
        redisTemplate.opsForSet().add("user:" + targetUid + ":muters", authenticatedUid);
        return added != null && added == 1;
    }

    public boolean unmute(String authenticatedUid, String targetUid) {
        targetUid = resolveTargetUid(targetUid, null);
        validateDistinctKnownUsers(authenticatedUid, targetUid);

        Long removed = redisTemplate.opsForSet().remove("user:" + authenticatedUid + ":muted", targetUid);
        redisTemplate.opsForSet().remove("user:" + targetUid + ":muters", authenticatedUid);
        return removed != null && removed == 1;
    }

    public boolean hasBlocked(String ownerUid, String targetUid) {
        Boolean isBlocked = redisTemplate.opsForSet().isMember("user:" + ownerUid + ":blocked", targetUid);
        return Boolean.TRUE.equals(isBlocked);
    }

    public boolean hasMuted(String ownerUid, String targetUid) {
        Boolean isMuted = redisTemplate.opsForSet().isMember("user:" + ownerUid + ":muted", targetUid);
        return Boolean.TRUE.equals(isMuted);
    }

    public boolean isImageBlocked(String ownerUid, String imageHash) {
        if (imageHash == null || imageHash.isBlank()) {
            return false;
        }

        return Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(
            "user:" + ownerUid + ":images:blocked:md5", imageHash));
    }

    public boolean hasNegativeKeyword(String ownerUid, List<String> keywords) {
        for (String keyword : keywords) {
            if (Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(
                    "user:" + ownerUid + ":negative:keywords", keyword))) {
                return true;
            }
        }
        return false;
    }

    public boolean canViewContent(String viewerUid, String actorUid) {
        return !hasBlocked(viewerUid, actorUid) && !hasBlocked(actorUid, viewerUid);
    }

    public void ensureAuthor(String authenticatedUid, String postUid) {
        if (!authenticatedUid.equals(postUid)) {
            throw new AccessDeniedException("Only the original author can modify this post");
        }
    }

    private String resolveTargetUid(String targetUid, String targetUsername) {
        if (targetUid != null && !targetUid.isBlank()) {
            return targetUid;
        }
        if (targetUsername != null && !targetUsername.isBlank()) {
            return getUid(targetUsername);
        }
        return null;
    }

    private String requireUsername(String uid) {
        String username = getUsername(uid);
        if (username == null) {
            throw new UserNotFoundException("User not found");
        }
        return username;
    }

    private void incrementCounterForUid(String uid, String field, long delta) {
        String username = getUsername(uid);
        if (username != null) {
            redisTemplate.opsForHash().increment("user:" + username, field, delta);
        }
    }

    private long getCounter(String uid, String field) {
        String username = getUsername(uid);
        if (username == null) {
            return 0L;
        }

        Object value = redisTemplate.opsForHash().get("user:" + username, field);
        if (value == null) {
            return 0L;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private Set<String> getFriends(String uid) {
        Set<String> followers = redisTemplate.opsForSet().members("user:" + uid + ":followers");
        Set<String> following = redisTemplate.opsForSet().members("user:" + uid + ":following");

        if (followers == null || following == null) {
            return new HashSet<>();
        }

        Set<String> friends = new HashSet<>(followers);
        friends.retainAll(following);
        return friends;
    }

    private void validateDistinctKnownUsers(String authenticatedUid, String targetUid) {
        if (targetUid == null || !uidExists(targetUid)) {
            throw new UserNotFoundException("User not found");
        }
        if (authenticatedUid.equals(targetUid)) {
            throw new CannotFollowSelfException("Cannot perform this action on yourself");
        }
    }

    private void removeFollowIfPresent(String followerUid, String targetUid) {
        Long removed = redisTemplate.opsForSet().remove("user:" + targetUid + ":followers", followerUid);
        if (removed != null && removed > 0) {
            redisTemplate.opsForSet().remove("user:" + followerUid + ":following", targetUid);
            incrementCounterForUid(targetUid, "followers", -1);
            incrementCounterForUid(followerUid, "following", -1);
        }
    }
}
