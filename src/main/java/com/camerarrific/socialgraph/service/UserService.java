package com.camerarrific.socialgraph.service;

import com.camerarrific.socialgraph.dto.request.FollowRequest;
import com.camerarrific.socialgraph.dto.response.MembersResponse;
import com.camerarrific.socialgraph.dto.response.UserResponse;
import com.camerarrific.socialgraph.exception.ApiException;
import com.camerarrific.socialgraph.model.User;
import com.camerarrific.socialgraph.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Service for user-related operations (follow, unfollow, member lists).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private static final String FOLLOWERS = "followers";
    private static final String FOLLOWING = "following";
    private static final String FRIENDS = "friends";
    private static final String BLOCKED = "blocked";
    private static final String BLOCKERS = "blockers";
    private static final String MUTED = "muted";
    private static final String MUTERS = "muters";

    private final UserRepository userRepository;

    /**
     * Get current user's profile.
     */
    public UserResponse getCurrentUser(String uid) {
        User user = userRepository.findByUid(uid)
                .orElseThrow(() -> new ApiException("user_not_found", "User not found", HttpStatus.NOT_FOUND));

        return toUserResponse(user);
    }

    /**
     * Get a user's profile by UID.
     */
    public UserResponse getUserByUid(String uid) {
        User user = userRepository.findByUid(uid)
                .orElseThrow(() -> new ApiException("user_not_found", "User not found", HttpStatus.NOT_FOUND));

        return toUserResponse(user);
    }

    /**
     * Follow another user.
     */
    public void follow(String authenticatedUid, FollowRequest request) {
        String targetUid = resolveTargetUid(request);

        // Cannot follow yourself
        if (authenticatedUid.equals(targetUid)) {
            throw new ApiException("cannot_follow", "Cannot follow yourself", HttpStatus.BAD_REQUEST);
        }

        // Check if already following
        if (userRepository.isMemberOfSet(targetUid, FOLLOWERS, authenticatedUid)) {
            throw new ApiException("cannot_follow", "Already following this user", HttpStatus.BAD_REQUEST);
        }

        // Add to followers set
        userRepository.addToSet(targetUid, FOLLOWERS, authenticatedUid);
        
        // Add to following set
        userRepository.addToSet(authenticatedUid, FOLLOWING, targetUid);

        // Update counters
        String targetUsername = userRepository.findUsernameByUid(targetUid).orElse(null);
        String authUsername = userRepository.findUsernameByUid(authenticatedUid).orElse(null);
        
        if (targetUsername != null) {
            userRepository.incrementField(targetUsername, FOLLOWERS, 1);
        }
        if (authUsername != null) {
            userRepository.incrementField(authUsername, FOLLOWING, 1);
        }

        log.info("User {} followed {}", authenticatedUid, targetUid);
    }

    /**
     * Unfollow a user.
     */
    public void unfollow(String authenticatedUid, FollowRequest request) {
        String targetUid = resolveTargetUid(request);

        // Cannot unfollow yourself
        if (authenticatedUid.equals(targetUid)) {
            throw new ApiException("cannot_unfollow", "Cannot unfollow yourself", HttpStatus.BAD_REQUEST);
        }

        // Check if actually following
        if (!userRepository.isMemberOfSet(targetUid, FOLLOWERS, authenticatedUid)) {
            throw new ApiException("cannot_unfollow", "Not following this user", HttpStatus.BAD_REQUEST);
        }

        // Remove from followers set
        userRepository.removeFromSet(targetUid, FOLLOWERS, authenticatedUid);
        
        // Remove from following set
        userRepository.removeFromSet(authenticatedUid, FOLLOWING, targetUid);

        // Update counters
        String targetUsername = userRepository.findUsernameByUid(targetUid).orElse(null);
        String authUsername = userRepository.findUsernameByUid(authenticatedUid).orElse(null);
        
        if (targetUsername != null) {
            userRepository.incrementField(targetUsername, FOLLOWERS, -1);
        }
        if (authUsername != null) {
            userRepository.incrementField(authUsername, FOLLOWING, -1);
        }

        log.info("User {} unfollowed {}", authenticatedUid, targetUid);
    }

    /**
     * Get members of a user's set (followers, following, friends, etc.).
     */
    public MembersResponse getMembers(String uid, String setName) {
        long startTime = System.currentTimeMillis();
        
        Set<String> memberUids = userRepository.getSetMembers(uid, setName);
        
        List<MembersResponse.Member> members = new ArrayList<>();
        for (String memberUid : memberUids) {
            userRepository.findUsernameByUid(memberUid).ifPresent(username -> {
                String fullname = userRepository.getField(username, "fullname").orElse(null);
                members.add(MembersResponse.Member.builder()
                        .uid(memberUid)
                        .username(username)
                        .fullname(fullname)
                        .build());
            });
        }

        long duration = System.currentTimeMillis() - startTime;
        
        return MembersResponse.builder()
                .members(members)
                .count(members.size())
                .duration(duration)
                .build();
    }

    /**
     * Get followers.
     */
    public MembersResponse getFollowers(String uid) {
        return getMembers(uid, FOLLOWERS);
    }

    /**
     * Get following.
     */
    public MembersResponse getFollowing(String uid) {
        return getMembers(uid, FOLLOWING);
    }

    /**
     * Get friends (mutual follows).
     */
    public MembersResponse getFriends(String uid) {
        return getMembers(uid, FRIENDS);
    }

    /**
     * Get blocked users.
     */
    public MembersResponse getBlocked(String uid) {
        return getMembers(uid, BLOCKED);
    }

    /**
     * Get blockers.
     */
    public MembersResponse getBlockers(String uid) {
        return getMembers(uid, BLOCKERS);
    }

    /**
     * Get muted users.
     */
    public MembersResponse getMuted(String uid) {
        return getMembers(uid, MUTED);
    }

    /**
     * Get muters.
     */
    public MembersResponse getMuters(String uid) {
        return getMembers(uid, MUTERS);
    }

    /**
     * Get user's public RSA key.
     */
    public String getPublicRsaKey(String uid) {
        return userRepository.getPublicRsaKey(uid)
                .orElseThrow(() -> new ApiException("key_not_found", "Public key not found", HttpStatus.NOT_FOUND));
    }

    /**
     * Get user's registered devices.
     */
    public Set<String> getDevices(String uid) {
        return userRepository.getDevices(uid);
    }

    /**
     * Add a negative keyword filter.
     */
    public boolean addNegativeKeyword(String uid, String keyword) {
        return userRepository.addNegativeKeyword(uid, keyword);
    }

    /**
     * Block an image by MD5.
     */
    public boolean blockImage(String uid, String md5) {
        return userRepository.blockImage(uid, md5);
    }

    private String resolveTargetUid(FollowRequest request) {
        if (request.getUid() != null && !request.getUid().isEmpty()) {
            // Verify user exists
            if (userRepository.findUsernameByUid(request.getUid()).isEmpty()) {
                throw new ApiException("user_not_found", "Cannot follow unknown user", HttpStatus.BAD_REQUEST);
            }
            return request.getUid();
        } else if (request.getUsername() != null && !request.getUsername().isEmpty()) {
            return userRepository.findByUsername(request.getUsername())
                    .map(User::getUid)
                    .orElseThrow(() -> new ApiException("user_not_found", "Cannot follow unknown user", HttpStatus.BAD_REQUEST));
        } else {
            throw new ApiException("incomplete_request", "Either uid or username is required", HttpStatus.BAD_REQUEST);
        }
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .uid(user.getUid())
                .username(user.getUsername())
                .fullname(user.getFullname())
                .email(user.getEmail())
                .followers(user.getFollowers())
                .following(user.getFollowing())
                .profilePicture(user.getProfilePicture())
                .build();
    }
}

