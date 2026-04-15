package com.intelligenta.socialgraph.controller;

import com.intelligenta.socialgraph.Verbs;
import com.intelligenta.socialgraph.model.ActionResponse;
import com.intelligenta.socialgraph.security.AuthenticatedUser;
import com.intelligenta.socialgraph.service.ActionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for social action endpoints (like, love, fav, share).
 */
@RestController
@RequestMapping("/api")
public class ActionController {

    private final ActionService actionService;

    public ActionController(ActionService actionService) {
        this.actionService = actionService;
    }

    // ========== LIKES ==========

    @GetMapping("/likes")
    public ResponseEntity<ActionResponse> getLikes(
            @RequestParam String uuid,
            @RequestParam int index,
            @RequestParam int count) {
        ActionResponse response = actionService.listActions(Verbs.Action.LIKE, uuid, index, count);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/like")
    public ResponseEntity<Map<String, String>> like(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam String uuid) {
        String result = actionService.performAction(Verbs.Action.LIKE, uuid, user.getUid());
        return ResponseEntity.ok(createActionResponse(result));
    }

    @PostMapping("/unlike")
    public ResponseEntity<Map<String, String>> unlike(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam String uuid) {
        String result = actionService.reverseAction(Verbs.Action.LIKE, uuid, user.getUid());
        return ResponseEntity.ok(createActionResponse(result));
    }

    // ========== LOVES ==========

    @GetMapping("/loves")
    public ResponseEntity<ActionResponse> getLoves(
            @RequestParam String uuid,
            @RequestParam int index,
            @RequestParam int count) {
        ActionResponse response = actionService.listActions(Verbs.Action.LOVE, uuid, index, count);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/love")
    public ResponseEntity<Map<String, String>> love(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam String uuid) {
        String result = actionService.performAction(Verbs.Action.LOVE, uuid, user.getUid());
        return ResponseEntity.ok(createActionResponse(result));
    }

    @PostMapping("/unlove")
    public ResponseEntity<Map<String, String>> unlove(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam String uuid) {
        String result = actionService.reverseAction(Verbs.Action.LOVE, uuid, user.getUid());
        return ResponseEntity.ok(createActionResponse(result));
    }

    // ========== FAVORITES ==========

    @GetMapping("/faves")
    public ResponseEntity<ActionResponse> getFaves(
            @RequestParam String uuid,
            @RequestParam int index,
            @RequestParam int count) {
        ActionResponse response = actionService.listActions(Verbs.Action.FAV, uuid, index, count);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/fav")
    public ResponseEntity<Map<String, String>> fav(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam String uuid) {
        String result = actionService.performAction(Verbs.Action.FAV, uuid, user.getUid());
        return ResponseEntity.ok(createActionResponse(result));
    }

    @PostMapping("/unfav")
    public ResponseEntity<Map<String, String>> unfav(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam String uuid) {
        String result = actionService.reverseAction(Verbs.Action.FAV, uuid, user.getUid());
        return ResponseEntity.ok(createActionResponse(result));
    }

    // ========== SHARES ==========

    @GetMapping("/shares")
    public ResponseEntity<ActionResponse> getShares(
            @RequestParam String uuid,
            @RequestParam int index,
            @RequestParam int count) {
        ActionResponse response = actionService.listActions(Verbs.Action.SHARE, uuid, index, count);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/share")
    public ResponseEntity<Map<String, String>> share(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam String uuid) {
        String result = actionService.performAction(Verbs.Action.SHARE, uuid, user.getUid());
        return ResponseEntity.ok(createActionResponse(result));
    }

    @PostMapping("/unshare")
    public ResponseEntity<Map<String, String>> unshare(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam String uuid) {
        String result = actionService.reverseAction(Verbs.Action.SHARE, uuid, user.getUid());
        return ResponseEntity.ok(createActionResponse(result));
    }

    private Map<String, String> createActionResponse(String message) {
        Map<String, String> response = new HashMap<>();
        response.put("result", message);
        return response;
    }
}
