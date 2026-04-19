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
import com.intelligenta.socialgraph.persistence.ContentFilterStore;
import com.intelligenta.socialgraph.persistence.RelationStore;
import com.intelligenta.socialgraph.persistence.RelationStore.Relation;
import com.intelligenta.socialgraph.persistence.TokenStore;
import com.intelligenta.socialgraph.persistence.UserStore;
import com.intelligenta.socialgraph.util.Util;
import org.springframework.security.access.AccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User-related operations. Refactored in phase I-D to delegate all persistence
 * through the store interfaces so the Infinispan-native impls landing in
 * phases I-F onward can swap in behind the same surface.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserStore users;
    private final RelationStore relations;
    private final ContentFilterStore filters;
    private final TokenStore tokens;
    private final AppProperties appProperties;

    public UserService(UserStore users,
                       RelationStore relations,
                       ContentFilterStore filters,
                       TokenStore tokens,
                       AppProperties appProperties) {
        this.users = users;
        this.relations = relations;
        this.filters = filters;
        this.tokens = tokens;
        this.appProperties = appProperties;
    }

    public AuthResponse register(String username, String password, String email) throws NoSuchAlgorithmException {
        if (users.exists(username)) {
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
        Duration ttl = Duration.ofSeconds(tokenExpiration);
        String activationToken = users.register(username, userHash, uid, token, ttl);

        AuthResponse response = new AuthResponse(username, token, uid, tokenExpiration);
        response.setActivationToken(activationToken);
        response.setFollowers("0");
        response.setFollowing("0");
        return response;
    }

    public AuthResponse login(String username, String password) {
        List<Optional<String>> fields = users.getFields(username,
            List.of("passwordHash", "salt", "poly"));

        if (fields.isEmpty() || fields.get(0).isEmpty()) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        String passwordHash = fields.get(0).get();
        String salt = fields.get(1).orElse("");

        if (!PasswordHash.validateArgon2Hash(salt + password, passwordHash)) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        String token = Util.UUID();
        List<Optional<String>> userFields = users.getFields(username,
            List.of("uuid", "followers", "following"));

        String uid = userFields.get(0).orElse(null);
        String followers = userFields.get(1).orElse("0");
        String following = userFields.get(2).orElse("0");

        long tokenExpiration = appProperties.getSecurity().getTokenExpirationSeconds();
        tokens.issue(token, uid, Duration.ofSeconds(tokenExpiration));

        AuthResponse response = new AuthResponse(username, token, uid, tokenExpiration);
        response.setFollowers(followers);
        response.setFollowing(following);
        return response;
    }

    public String authenticatedUser(String token) {
        return tokens.resolve(token).orElse(null);
    }

    public String getUsername(String uid) {
        return users.findUsernameByUid(uid).orElse(null);
    }

    public String getUid(String username) {
        return users.findUidByUsername(username).orElse(null);
    }

    public boolean userExists(String username) { return users.exists(username); }
    public boolean uidExists(String uid)        { return users.uidExists(uid); }

    public void follow(String authenticatedUid, String targetUid, String targetUsername) {
        targetUid = resolveTargetUid(targetUid, targetUsername);
        if (targetUid == null || !users.uidExists(targetUid)) {
            throw new UserNotFoundException("User not found");
        }
        if (authenticatedUid.equals(targetUid)) {
            throw new CannotFollowSelfException("Cannot follow yourself");
        }

        boolean added = relations.add(targetUid, Relation.FOLLOWERS, authenticatedUid);
        if (!added) {
            throw new AlreadyFollowingException("Already following this user");
        }
        relations.add(authenticatedUid, Relation.FOLLOWING, targetUid);
        incrementCounterForUid(targetUid, "followers", 1);
        incrementCounterForUid(authenticatedUid, "following", 1);
    }

    public void unfollow(String authenticatedUid, String targetUid) {
        if (authenticatedUid.equals(targetUid)) {
            throw new CannotFollowSelfException("Cannot unfollow yourself");
        }
        boolean removed = relations.remove(targetUid, Relation.FOLLOWERS, authenticatedUid);
        if (!removed) {
            throw new NotFollowingException("Not following this user");
        }
        relations.remove(authenticatedUid, Relation.FOLLOWING, targetUid);
        incrementCounterForUid(targetUid, "followers", -1);
        incrementCounterForUid(authenticatedUid, "following", -1);
    }

    public List<MemberInfo> getMembers(String uid, String setType) {
        long startTime = System.currentTimeMillis();
        Set<String> memberUids = "friends".equals(setType)
            ? getFriends(uid)
            : relations.members(uid, parseRelation(setType));

        List<MemberInfo> members = new ArrayList<>();
        for (String memberUid : memberUids) {
            String username = getUsername(memberUid);
            String fullname = username == null ? null : users.getField(username, "fullname").orElse(null);
            members.add(new MemberInfo(memberUid, username, fullname));
        }
        log.debug("getMembers({}, {}) took {}ms", uid, setType, System.currentTimeMillis() - startTime);
        return members;
    }

    public String getPublicRSAKey(String uid) {
        return users.getPublicRsaKey(uid).orElse(null);
    }

    public String getUserField(String uid, String field) {
        String username = getUsername(uid);
        if (username == null) return null;
        return users.getField(username, field).orElse(null);
    }

    public Map<String, String> activateAccount(String activationToken) {
        String uid = users.consumeActivationToken(activationToken).orElse(null);
        String username = uid != null ? getUsername(uid) : null;

        Map<String, String> result = new HashMap<>();
        if (username != null) {
            result.put("username", username);
            result.put("uid", uid);
            result.put("activated", "true");
            users.putField(username, "activated", "true");
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

        if (fullname != null)       updates.put("fullname", fullname);
        if (bio != null)            updates.put("bio", bio);
        if (profilePicture != null) updates.put("profilePicture", profilePicture);

        if (!updates.isEmpty()) {
            users.putAll(username, updates);
        }
        return getProfile(uid, uid);
    }

    public List<MemberInfo> searchUsers(String query, int index, int count) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase();
        List<MemberInfo> matches = users.allUidToUsername().entrySet().stream()
            .map(entry -> new MemberInfo(entry.getKey(), entry.getValue(), null))
            .filter(member -> normalizedQuery.isEmpty()
                || member.getUsername().toLowerCase().contains(normalizedQuery)
                || String.valueOf(getUserField(member.getUid(), "fullname")).toLowerCase().contains(normalizedQuery))
            .sorted((left, right) -> left.getUsername().compareToIgnoreCase(right.getUsername()))
            .collect(Collectors.toList());

        int fromIndex = Math.max(0, index);
        int toIndex = Math.min(matches.size(), fromIndex + Math.max(count, 0));
        if (fromIndex >= matches.size()) return new ArrayList<>();

        List<MemberInfo> page = new ArrayList<>();
        for (MemberInfo member : matches.subList(fromIndex, toIndex)) {
            page.add(new MemberInfo(member.getUid(), member.getUsername(),
                getUserField(member.getUid(), "fullname")));
        }
        return page;
    }

    public boolean block(String authenticatedUid, String targetUid) {
        targetUid = resolveTargetUid(targetUid, null);
        validateDistinctKnownUsers(authenticatedUid, targetUid);
        boolean added = relations.add(authenticatedUid, Relation.BLOCKED, targetUid);
        relations.add(targetUid, Relation.BLOCKERS, authenticatedUid);
        removeFollowIfPresent(authenticatedUid, targetUid);
        removeFollowIfPresent(targetUid, authenticatedUid);
        return added;
    }

    public boolean unblock(String authenticatedUid, String targetUid) {
        targetUid = resolveTargetUid(targetUid, null);
        validateDistinctKnownUsers(authenticatedUid, targetUid);
        boolean removed = relations.remove(authenticatedUid, Relation.BLOCKED, targetUid);
        relations.remove(targetUid, Relation.BLOCKERS, authenticatedUid);
        return removed;
    }

    public boolean mute(String authenticatedUid, String targetUid) {
        targetUid = resolveTargetUid(targetUid, null);
        validateDistinctKnownUsers(authenticatedUid, targetUid);
        boolean added = relations.add(authenticatedUid, Relation.MUTED, targetUid);
        relations.add(targetUid, Relation.MUTERS, authenticatedUid);
        return added;
    }

    public boolean unmute(String authenticatedUid, String targetUid) {
        targetUid = resolveTargetUid(targetUid, null);
        validateDistinctKnownUsers(authenticatedUid, targetUid);
        boolean removed = relations.remove(authenticatedUid, Relation.MUTED, targetUid);
        relations.remove(targetUid, Relation.MUTERS, authenticatedUid);
        return removed;
    }

    public boolean hasBlocked(String ownerUid, String targetUid) {
        return relations.contains(ownerUid, Relation.BLOCKED, targetUid);
    }

    public boolean hasMuted(String ownerUid, String targetUid) {
        return relations.contains(ownerUid, Relation.MUTED, targetUid);
    }

    public boolean isImageBlocked(String ownerUid, String imageHash) {
        return filters.isImageBlocked(ownerUid, imageHash);
    }

    public boolean hasNegativeKeyword(String ownerUid, List<String> keywords) {
        return filters.hasAnyNegativeKeyword(ownerUid, keywords);
    }

    public boolean canViewContent(String viewerUid, String actorUid) {
        return !hasBlocked(viewerUid, actorUid) && !hasBlocked(actorUid, viewerUid);
    }

    public void ensureAuthor(String authenticatedUid, String postUid) {
        if (!authenticatedUid.equals(postUid)) {
            throw new AccessDeniedException("Only the original author can modify this post");
        }
    }

    // Accessor used by ShareService to fan-out to followers.
    public Set<String> followerUids(String uid) {
        return relations.members(uid, Relation.FOLLOWERS);
    }

    private Relation parseRelation(String setType) {
        return switch (setType) {
            case "followers" -> Relation.FOLLOWERS;
            case "following" -> Relation.FOLLOWING;
            case "blocked"   -> Relation.BLOCKED;
            case "blockers"  -> Relation.BLOCKERS;
            case "muted"     -> Relation.MUTED;
            case "muters"    -> Relation.MUTERS;
            default          -> throw new IllegalArgumentException("Unknown relation: " + setType);
        };
    }

    private String resolveTargetUid(String targetUid, String targetUsername) {
        if (targetUid != null && !targetUid.isBlank()) return targetUid;
        if (targetUsername != null && !targetUsername.isBlank()) return getUid(targetUsername);
        return null;
    }

    private String requireUsername(String uid) {
        String username = getUsername(uid);
        if (username == null) throw new UserNotFoundException("User not found");
        return username;
    }

    private void incrementCounterForUid(String uid, String field, long delta) {
        String username = getUsername(uid);
        if (username != null) users.incrementField(username, field, delta);
    }

    private long getCounter(String uid, String field) {
        String username = getUsername(uid);
        if (username == null) return 0L;
        return users.getField(username, field)
            .map(v -> { try { return Long.parseLong(v); } catch (NumberFormatException e) { return 0L; } })
            .orElse(0L);
    }

    private Set<String> getFriends(String uid) {
        Set<String> followers = relations.members(uid, Relation.FOLLOWERS);
        Set<String> following = relations.members(uid, Relation.FOLLOWING);
        Set<String> friends = new HashSet<>(followers);
        friends.retainAll(following);
        return friends;
    }

    private void validateDistinctKnownUsers(String authenticatedUid, String targetUid) {
        if (targetUid == null || !users.uidExists(targetUid)) {
            throw new UserNotFoundException("User not found");
        }
        if (authenticatedUid.equals(targetUid)) {
            throw new CannotFollowSelfException("Cannot perform this action on yourself");
        }
    }

    private void removeFollowIfPresent(String followerUid, String targetUid) {
        boolean removed = relations.remove(targetUid, Relation.FOLLOWERS, followerUid);
        if (removed) {
            relations.remove(followerUid, Relation.FOLLOWING, targetUid);
            incrementCounterForUid(targetUid, "followers", -1);
            incrementCounterForUid(followerUid, "following", -1);
        }
    }
}
