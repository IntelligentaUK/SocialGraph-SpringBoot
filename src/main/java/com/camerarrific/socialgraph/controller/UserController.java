package com.camerarrific.socialgraph.controller;

import com.camerarrific.socialgraph.dto.request.FollowRequest;
import com.camerarrific.socialgraph.dto.response.ErrorResponse;
import com.camerarrific.socialgraph.dto.response.MembersResponse;
import com.camerarrific.socialgraph.dto.response.UserResponse;
import com.camerarrific.socialgraph.security.UserPrincipal;
import com.camerarrific.socialgraph.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

/**
 * Controller for user-related endpoints.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Users", description = "User management and social connections")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Get Current User", description = "Get the authenticated user's profile")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Success",
            content = @Content(schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userService.getCurrentUser(principal.getUid()));
    }

    @GetMapping("/user/{uid}")
    @Operation(summary = "Get User", description = "Get a user's profile by UID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Success",
            content = @Content(schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "404", description = "User not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserResponse> getUserByUid(@PathVariable String uid) {
        return ResponseEntity.ok(userService.getUserByUid(uid));
    }

    @PostMapping("/follow")
    @Operation(summary = "Follow User", description = "Follow another user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully followed"),
        @ApiResponse(responseCode = "400", description = "Cannot follow (already following, unknown user, or self)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Map<String, Object>> follow(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody FollowRequest request) {
        userService.follow(principal.getUid(), request);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "following.uid", request.getUid() != null ? request.getUid() : "",
            "following.username", request.getUsername() != null ? request.getUsername() : ""
        ));
    }

    @PostMapping("/unfollow")
    @Operation(summary = "Unfollow User", description = "Unfollow a user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully unfollowed"),
        @ApiResponse(responseCode = "400", description = "Cannot unfollow",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Map<String, Object>> unfollow(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody FollowRequest request) {
        userService.unfollow(principal.getUid(), request);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "unfollowed", request.getUid() != null ? request.getUid() : request.getUsername()
        ));
    }

    @GetMapping("/followers")
    @Operation(summary = "Get Followers", description = "Get list of followers")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Success",
            content = @Content(schema = @Schema(implementation = MembersResponse.class)))
    })
    public ResponseEntity<MembersResponse> getFollowers(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userService.getFollowers(principal.getUid()));
    }

    @GetMapping("/following")
    @Operation(summary = "Get Following", description = "Get list of users being followed")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Success",
            content = @Content(schema = @Schema(implementation = MembersResponse.class)))
    })
    public ResponseEntity<MembersResponse> getFollowing(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userService.getFollowing(principal.getUid()));
    }

    @GetMapping("/friends")
    @Operation(summary = "Get Friends", description = "Get list of mutual follows (friends)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Success",
            content = @Content(schema = @Schema(implementation = MembersResponse.class)))
    })
    public ResponseEntity<MembersResponse> getFriends(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userService.getFriends(principal.getUid()));
    }

    @GetMapping("/blocked")
    @Operation(summary = "Get Blocked Users", description = "Get list of blocked users")
    public ResponseEntity<MembersResponse> getBlocked(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userService.getBlocked(principal.getUid()));
    }

    @GetMapping("/blockers")
    @Operation(summary = "Get Blockers", description = "Get list of users who blocked you")
    public ResponseEntity<MembersResponse> getBlockers(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userService.getBlockers(principal.getUid()));
    }

    @GetMapping("/muted")
    @Operation(summary = "Get Muted Users", description = "Get list of muted users")
    public ResponseEntity<MembersResponse> getMuted(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userService.getMuted(principal.getUid()));
    }

    @GetMapping("/muters")
    @Operation(summary = "Get Muters", description = "Get list of users who muted you")
    public ResponseEntity<MembersResponse> getMuters(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userService.getMuters(principal.getUid()));
    }

    @GetMapping("/me/rsa/public/key")
    @Operation(summary = "Get My Public RSA Key", description = "Get the authenticated user's public RSA key")
    public ResponseEntity<Map<String, String>> getMyPublicKey(@AuthenticationPrincipal UserPrincipal principal) {
        String publicKey = userService.getPublicRsaKey(principal.getUid());
        return ResponseEntity.ok(Map.of("publicKey", publicKey));
    }

    @GetMapping("/rsa/public/key")
    @Operation(summary = "Get Public RSA Key", description = "Get a user's public RSA key")
    public ResponseEntity<Map<String, String>> getPublicKey(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String uid) {
        String targetUid = uid != null ? uid : principal.getUid();
        String publicKey = userService.getPublicRsaKey(targetUid);
        return ResponseEntity.ok(Map.of("publicKey", publicKey));
    }

    @GetMapping("/devices/registered")
    @Operation(summary = "Get Registered Devices", description = "Get list of registered devices")
    public ResponseEntity<Map<String, Object>> getDevices(@AuthenticationPrincipal UserPrincipal principal) {
        long startTime = System.currentTimeMillis();
        Set<String> devices = userService.getDevices(principal.getUid());
        long duration = System.currentTimeMillis() - startTime;
        
        return ResponseEntity.ok(Map.of(
            "devices", devices,
            "count", devices.size(),
            "duration", duration
        ));
    }

    @PostMapping("/add/keyword/negative")
    @Operation(summary = "Add Negative Keyword", description = "Add a keyword to filter from timeline")
    public ResponseEntity<Map<String, Object>> addNegativeKeyword(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String keyword) {
        boolean added = userService.addNegativeKeyword(principal.getUid(), keyword);
        return ResponseEntity.ok(Map.of(
            "keyword", keyword,
            "added", added
        ));
    }

    @PostMapping("/add/image/block")
    @Operation(summary = "Block Image", description = "Block an image by MD5 hash")
    public ResponseEntity<Map<String, Object>> blockImage(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String md5) {
        boolean blocked = userService.blockImage(principal.getUid(), md5);
        return ResponseEntity.ok(Map.of(
            "md5", md5,
            "blocked", blocked
        ));
    }
}

