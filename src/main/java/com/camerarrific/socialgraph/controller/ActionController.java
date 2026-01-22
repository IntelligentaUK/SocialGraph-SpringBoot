package com.camerarrific.socialgraph.controller;

import com.camerarrific.socialgraph.dto.request.ActionRequest;
import com.camerarrific.socialgraph.dto.response.ActionResponse;
import com.camerarrific.socialgraph.dto.response.ErrorResponse;
import com.camerarrific.socialgraph.model.Action;
import com.camerarrific.socialgraph.security.UserPrincipal;
import com.camerarrific.socialgraph.service.ActionService;
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

/**
 * Controller for social action endpoints (like, love, fav, share).
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Actions", description = "Social actions (like, love, fav, share)")
@SecurityRequirement(name = "bearerAuth")
public class ActionController {

    private final ActionService actionService;

    // ================= LIKES =================

    @GetMapping("/likes")
    @Operation(summary = "Get Likes", description = "Get list of users who liked a post")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Success",
            content = @Content(schema = @Schema(implementation = ActionResponse.class))),
        @ApiResponse(responseCode = "404", description = "Post not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ActionResponse> getLikes(
            @RequestParam String uuid,
            @RequestParam(defaultValue = "0") int index,
            @RequestParam(defaultValue = "20") int count) {
        return ResponseEntity.ok(actionService.listActions(Action.LIKE, uuid, index, count));
    }

    @PostMapping("/like")
    @Operation(summary = "Like Post", description = "Like a post")
    public ResponseEntity<Map<String, String>> like(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ActionRequest request) {
        return ResponseEntity.ok(actionService.performAction(Action.LIKE, request.getUuid(), principal.getUid()));
    }

    @PostMapping("/unlike")
    @Operation(summary = "Unlike Post", description = "Remove like from a post")
    public ResponseEntity<Map<String, String>> unlike(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ActionRequest request) {
        return ResponseEntity.ok(actionService.reverseAction(Action.LIKE, request.getUuid(), principal.getUid()));
    }

    // ================= LOVES =================

    @GetMapping("/loves")
    @Operation(summary = "Get Loves", description = "Get list of users who loved a post")
    public ResponseEntity<ActionResponse> getLoves(
            @RequestParam String uuid,
            @RequestParam(defaultValue = "0") int index,
            @RequestParam(defaultValue = "20") int count) {
        return ResponseEntity.ok(actionService.listActions(Action.LOVE, uuid, index, count));
    }

    @PostMapping("/love")
    @Operation(summary = "Love Post", description = "Love a post")
    public ResponseEntity<Map<String, String>> love(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ActionRequest request) {
        return ResponseEntity.ok(actionService.performAction(Action.LOVE, request.getUuid(), principal.getUid()));
    }

    @PostMapping("/unlove")
    @Operation(summary = "Unlove Post", description = "Remove love from a post")
    public ResponseEntity<Map<String, String>> unlove(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ActionRequest request) {
        return ResponseEntity.ok(actionService.reverseAction(Action.LOVE, request.getUuid(), principal.getUid()));
    }

    // ================= FAVS =================

    @GetMapping("/faves")
    @Operation(summary = "Get Faves", description = "Get list of users who favorited a post")
    public ResponseEntity<ActionResponse> getFaves(
            @RequestParam String uuid,
            @RequestParam(defaultValue = "0") int index,
            @RequestParam(defaultValue = "20") int count) {
        return ResponseEntity.ok(actionService.listActions(Action.FAV, uuid, index, count));
    }

    @PostMapping("/fav")
    @Operation(summary = "Favorite Post", description = "Favorite a post")
    public ResponseEntity<Map<String, String>> fav(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ActionRequest request) {
        return ResponseEntity.ok(actionService.performAction(Action.FAV, request.getUuid(), principal.getUid()));
    }

    @PostMapping("/unfav")
    @Operation(summary = "Unfavorite Post", description = "Remove favorite from a post")
    public ResponseEntity<Map<String, String>> unfav(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ActionRequest request) {
        return ResponseEntity.ok(actionService.reverseAction(Action.FAV, request.getUuid(), principal.getUid()));
    }

    // ================= SHARES =================

    @GetMapping("/shares")
    @Operation(summary = "Get Shares", description = "Get list of users who shared a post")
    public ResponseEntity<ActionResponse> getShares(
            @RequestParam String uuid,
            @RequestParam(defaultValue = "0") int index,
            @RequestParam(defaultValue = "20") int count) {
        return ResponseEntity.ok(actionService.listActions(Action.SHARE, uuid, index, count));
    }

    @PostMapping("/share")
    @Operation(summary = "Share Post", description = "Share a post")
    public ResponseEntity<Map<String, String>> share(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ActionRequest request) {
        return ResponseEntity.ok(actionService.performAction(Action.SHARE, request.getUuid(), principal.getUid()));
    }

    @PostMapping("/unshare")
    @Operation(summary = "Unshare Post", description = "Remove share from a post")
    public ResponseEntity<Map<String, String>> unshare(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ActionRequest request) {
        return ResponseEntity.ok(actionService.reverseAction(Action.SHARE, request.getUuid(), principal.getUid()));
    }
}

