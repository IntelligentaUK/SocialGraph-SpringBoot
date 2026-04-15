package com.intelligenta.socialgraph.controller;

import com.intelligenta.socialgraph.model.MemberInfo;
import com.intelligenta.socialgraph.model.MembersResponse;
import com.intelligenta.socialgraph.security.AuthenticatedUser;
import com.intelligenta.socialgraph.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for user-related endpoints.
 */
@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Follow a user.
     */
    @PostMapping("/follow")
    public ResponseEntity<Map<String, String>> follow(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String uid,
            @RequestParam(required = false) String username) {
        String targetUid = uid != null ? uid : userService.getUid(username);
        
        userService.follow(user.getUid(), uid, username);
        
        Map<String, String> response = new HashMap<>();
        response.put("success", "true");
        response.put("following.UID", targetUid);
        response.put("following.Username", username != null ? username : userService.getUsername(targetUid));
        
        return ResponseEntity.ok(response);
    }

    /**
     * Unfollow a user.
     */
    @PostMapping("/unfollow")
    public ResponseEntity<Map<String, String>> unfollow(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam String uid) {
        
        userService.unfollow(user.getUid(), uid);
        
        Map<String, String> response = new HashMap<>();
        response.put("success", uid);
        response.put("unfollowed", uid);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get followers list.
     */
    @GetMapping("/followers")
    public ResponseEntity<MembersResponse> getFollowers(
            @AuthenticationPrincipal AuthenticatedUser user) {
        long startTime = System.currentTimeMillis();
        List<MemberInfo> members = userService.getMembers(user.getUid(), "followers");
        long duration = System.currentTimeMillis() - startTime;
        return ResponseEntity.ok(new MembersResponse("followers", members, duration));
    }

    /**
     * Get following list.
     */
    @GetMapping("/following")
    public ResponseEntity<MembersResponse> getFollowing(
            @AuthenticationPrincipal AuthenticatedUser user) {
        long startTime = System.currentTimeMillis();
        List<MemberInfo> members = userService.getMembers(user.getUid(), "following");
        long duration = System.currentTimeMillis() - startTime;
        return ResponseEntity.ok(new MembersResponse("following", members, duration));
    }

    /**
     * Get friends list.
     */
    @GetMapping("/friends")
    public ResponseEntity<MembersResponse> getFriends(
            @AuthenticationPrincipal AuthenticatedUser user) {
        long startTime = System.currentTimeMillis();
        List<MemberInfo> members = userService.getMembers(user.getUid(), "friends");
        long duration = System.currentTimeMillis() - startTime;
        return ResponseEntity.ok(new MembersResponse("friends", members, duration));
    }

    /**
     * Get blocked users list.
     */
    @GetMapping("/blocked")
    public ResponseEntity<MembersResponse> getBlocked(
            @AuthenticationPrincipal AuthenticatedUser user) {
        long startTime = System.currentTimeMillis();
        List<MemberInfo> members = userService.getMembers(user.getUid(), "blocked");
        long duration = System.currentTimeMillis() - startTime;
        return ResponseEntity.ok(new MembersResponse("blocked", members, duration));
    }

    /**
     * Get users who blocked the authenticated user.
     */
    @GetMapping("/blockers")
    public ResponseEntity<MembersResponse> getBlockers(
            @AuthenticationPrincipal AuthenticatedUser user) {
        long startTime = System.currentTimeMillis();
        List<MemberInfo> members = userService.getMembers(user.getUid(), "blockers");
        long duration = System.currentTimeMillis() - startTime;
        return ResponseEntity.ok(new MembersResponse("blockers", members, duration));
    }

    /**
     * Get muted users list.
     */
    @GetMapping("/muted")
    public ResponseEntity<MembersResponse> getMuted(
            @AuthenticationPrincipal AuthenticatedUser user) {
        long startTime = System.currentTimeMillis();
        List<MemberInfo> members = userService.getMembers(user.getUid(), "muted");
        long duration = System.currentTimeMillis() - startTime;
        return ResponseEntity.ok(new MembersResponse("muted", members, duration));
    }

    /**
     * Get users who muted the authenticated user.
     */
    @GetMapping("/muters")
    public ResponseEntity<MembersResponse> getMuters(
            @AuthenticationPrincipal AuthenticatedUser user) {
        long startTime = System.currentTimeMillis();
        List<MemberInfo> members = userService.getMembers(user.getUid(), "muters");
        long duration = System.currentTimeMillis() - startTime;
        return ResponseEntity.ok(new MembersResponse("muters", members, duration));
    }

    /**
     * Get authenticated user's public RSA key.
     */
    @GetMapping("/me/rsa/public/key")
    public ResponseEntity<String> getMyPublicRSAKey(
            @AuthenticationPrincipal AuthenticatedUser user) {
        String key = userService.getPublicRSAKey(user.getUid());
        return ResponseEntity.ok(key);
    }

    /**
     * Get a user's public RSA key.
     */
    @GetMapping("/rsa/public/key")
    public ResponseEntity<String> getPublicRSAKey(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String uid) {
        String targetUid = uid != null ? uid : user.getUid();
        String key = userService.getPublicRSAKey(targetUid);
        return ResponseEntity.ok(key);
    }

    /**
     * Get the authenticated user's profile.
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMe(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(userService.getProfile(user.getUid(), user.getUid()));
    }

    /**
     * Update profile fields.
     */
    @PatchMapping("/me")
    public ResponseEntity<Map<String, Object>> updateMe(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String fullname,
            @RequestParam(required = false) String bio,
            @RequestParam(required = false) String profilePicture) {
        return ResponseEntity.ok(userService.updateProfile(user.getUid(), fullname, bio, profilePicture));
    }

    /**
     * Get another user's profile.
     */
    @GetMapping("/users/{uid}")
    public ResponseEntity<Map<String, Object>> getUser(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String uid) {
        return ResponseEntity.ok(userService.getProfile(uid, user.getUid()));
    }

    /**
     * Search users.
     */
    @GetMapping("/users/search")
    public ResponseEntity<MembersResponse> searchUsers(
            @RequestParam String q,
            @RequestParam int index,
            @RequestParam int count) {
        long startTime = System.currentTimeMillis();
        List<MemberInfo> members = userService.searchUsers(q, index, count);
        long duration = System.currentTimeMillis() - startTime;
        return ResponseEntity.ok(new MembersResponse("search", members, duration));
    }

    /**
     * Block a user.
     */
    @PostMapping("/block")
    public ResponseEntity<Map<String, String>> block(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam String uid) {
        return ResponseEntity.ok(createRelationResponse("blocked", uid, userService.block(user.getUid(), uid)));
    }

    /**
     * Unblock a user.
     */
    @PostMapping("/unblock")
    public ResponseEntity<Map<String, String>> unblock(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam String uid) {
        return ResponseEntity.ok(createRelationResponse("unblocked", uid, userService.unblock(user.getUid(), uid)));
    }

    /**
     * Mute a user.
     */
    @PostMapping("/mute")
    public ResponseEntity<Map<String, String>> mute(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam String uid) {
        return ResponseEntity.ok(createRelationResponse("muted", uid, userService.mute(user.getUid(), uid)));
    }

    /**
     * Unmute a user.
     */
    @PostMapping("/unmute")
    public ResponseEntity<Map<String, String>> unmute(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam String uid) {
        return ResponseEntity.ok(createRelationResponse("unmuted", uid, userService.unmute(user.getUid(), uid)));
    }

    private Map<String, String> createRelationResponse(String action, String uid, boolean changed) {
        Map<String, String> response = new HashMap<>();
        response.put("uid", uid);
        response.put("action", action);
        response.put("changed", changed ? "true" : "false");
        return response;
    }
}
