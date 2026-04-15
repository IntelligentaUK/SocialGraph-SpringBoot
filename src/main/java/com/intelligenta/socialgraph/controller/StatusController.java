package com.intelligenta.socialgraph.controller;

import com.intelligenta.socialgraph.security.AuthenticatedUser;
import com.intelligenta.socialgraph.service.ShareService;
import com.intelligenta.socialgraph.service.TimelineService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Controller for status/post and device endpoints.
 */
@RestController
@RequestMapping("/api")
public class StatusController {

    private final ShareService shareService;
    private final TimelineService timelineService;
    private final StringRedisTemplate redisTemplate;

    public StatusController(ShareService shareService,
                            TimelineService timelineService,
                            StringRedisTemplate redisTemplate) {
        this.shareService = shareService;
        this.timelineService = timelineService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Post a status update (photo or video).
     */
    @PostMapping("/status")
    public ResponseEntity<Map<String, String>> postStatus(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String content,
            @RequestParam String type,
            @RequestParam(required = false) String url,
            @RequestHeader(value = "Content-Type", required = false) String requestContentType,
            @RequestBody(required = false) byte[] body) {

        Map<String, String> result;
        
        if ("text".equals(type)) {
            if (content == null || content.isBlank()) {
                return ResponseEntity.badRequest().build();
            }
            result = shareService.shareText(user.getUid(), content);
        } else if ("photo".equals(type)) {
            if (url != null) {
                result = shareService.sharePhoto(user.getUid(), content, url);
            } else if (body != null && body.length > 0) {
                result = shareService.sharePhoto(user.getUid(), content, body, requestContentType);
            } else {
                return ResponseEntity.badRequest().build();
            }
        } else if ("video".equals(type)) {
            if (url == null || url.isBlank()) {
                return ResponseEntity.badRequest().build();
            }
            result = shareService.shareVideo(user.getUid(), content, url);
        } else {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Get a single post.
     */
    @GetMapping("/posts/{postId}")
    public ResponseEntity<com.intelligenta.socialgraph.model.TimelineEntry> getPost(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String postId) {
        return ResponseEntity.ok(timelineService.getPost(user.getUid(), postId));
    }

    /**
     * Get replies for a post.
     */
    @GetMapping("/posts/{postId}/replies")
    public ResponseEntity<com.intelligenta.socialgraph.model.TimelineResponse> getReplies(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String postId,
            @RequestParam int index,
            @RequestParam int count) {
        return ResponseEntity.ok(timelineService.getReplies(user.getUid(), postId, index, count));
    }

    /**
     * Reply to a post.
     */
    @PostMapping("/posts/{postId}/reply")
    public ResponseEntity<Map<String, String>> replyToPost(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String postId,
            @RequestParam String content) {
        if (content.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(shareService.replyToPost(user.getUid(), postId, content));
    }

    /**
     * Reshare a post.
     */
    @PostMapping("/posts/{postId}/reshare")
    public ResponseEntity<Map<String, String>> resharePost(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String postId,
            @RequestParam(required = false) String content) {
        return ResponseEntity.ok(shareService.resharePost(user.getUid(), postId, content));
    }

    /**
     * Edit a post.
     */
    @PatchMapping("/posts/{postId}")
    public ResponseEntity<Map<String, String>> editPost(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String postId,
            @RequestParam String content) {
        if (content.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(shareService.updatePost(user.getUid(), postId, content));
    }

    /**
     * Delete a post.
     */
    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<Map<String, String>> deletePost(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String postId) {
        return ResponseEntity.ok(shareService.deletePost(user.getUid(), postId));
    }

    /**
     * Get registered devices.
     */
    @GetMapping("/devices/registered")
    public ResponseEntity<Map<String, Object>> getRegisteredDevices(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String token,
            @RequestParam int index,
            @RequestParam int count) {
        
        long startTime = System.currentTimeMillis();
        
        Set<String> deviceSet = redisTemplate.opsForSet().members(
            "user:" + user.getUid() + ":devices");
        
        Map<String, Object> response = new HashMap<>();
        response.put("devices", deviceSet);
        response.put("count", deviceSet != null ? deviceSet.size() : 0);
        response.put("duration", System.currentTimeMillis() - startTime);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Add a negative keyword filter.
     */
    @PostMapping("/add/keyword/negative")
    public ResponseEntity<Map<String, String>> addNegativeKeyword(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam String keyword) {
        
        Map<String, String> response = new HashMap<>();
        response.put("keyword", keyword);
        
        Boolean added = redisTemplate.opsForHash().putIfAbsent(
            "user:" + user.getUid() + ":negative:keywords", 
            keyword, 
            keyword
        );
        
        response.put("added", Boolean.TRUE.equals(added) ? "true" : "false");
        return ResponseEntity.ok(response);
    }

    /**
     * Block an image by MD5 hash.
     */
    @PostMapping("/add/image/block")
    public ResponseEntity<Map<String, String>> addImageBlock(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String md5,
            @RequestParam(required = false) String postId) {
        String imageHash = md5;
        if ((imageHash == null || imageHash.isBlank()) && postId != null) {
            Map<String, String> post = shareService.getPost(postId);
            imageHash = post.get("imageHash");
            if (imageHash == null) {
                imageHash = post.get("md5");
            }
        }
        if (imageHash == null || imageHash.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        
        Map<String, String> response = new HashMap<>();
        response.put("imageHash", imageHash);
        
        Boolean added = redisTemplate.opsForHash().putIfAbsent(
            "user:" + user.getUid() + ":images:blocked:md5", 
            imageHash,
            imageHash
        );
        
        response.put("added", Boolean.TRUE.equals(added) ? "true" : "false");
        return ResponseEntity.ok(response);
    }
}
