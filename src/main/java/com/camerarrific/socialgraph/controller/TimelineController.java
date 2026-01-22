package com.camerarrific.socialgraph.controller;

import com.camerarrific.socialgraph.dto.request.StatusRequest;
import com.camerarrific.socialgraph.dto.response.ErrorResponse;
import com.camerarrific.socialgraph.dto.response.TimelineResponse;
import com.camerarrific.socialgraph.model.Post;
import com.camerarrific.socialgraph.security.UserPrincipal;
import com.camerarrific.socialgraph.service.ShareService;
import com.camerarrific.socialgraph.service.TimelineService;
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
 * Controller for timeline and status endpoints.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Timeline", description = "Timeline and status update endpoints")
@SecurityRequirement(name = "bearerAuth")
public class TimelineController {

    private final TimelineService timelineService;
    private final ShareService shareService;

    @GetMapping("/timeline")
    @Operation(summary = "Get Timeline", description = "Get the user's timeline (FIFO order)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Success",
            content = @Content(schema = @Schema(implementation = TimelineResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TimelineResponse> getTimeline(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int index,
            @RequestParam(defaultValue = "20") int count) {
        
        TimelineResponse response = timelineService.getTimeline(principal.getUid(), index, count);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/timeline/personal")
    @Operation(summary = "Get Personal Timeline", description = "Get timeline sorted by personal importance")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Success",
            content = @Content(schema = @Schema(implementation = TimelineResponse.class)))
    })
    public ResponseEntity<TimelineResponse> getPersonalTimeline(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int index,
            @RequestParam(defaultValue = "20") int count) {
        
        TimelineResponse response = timelineService.getTimelineByImportance(
                principal.getUid(), 
                TimelineService.ImportanceType.PERSONAL, 
                index, 
                count
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/timeline/everyone")
    @Operation(summary = "Get Everyone Timeline", description = "Get timeline sorted by global importance")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Success",
            content = @Content(schema = @Schema(implementation = TimelineResponse.class)))
    })
    public ResponseEntity<TimelineResponse> getEveryoneTimeline(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int index,
            @RequestParam(defaultValue = "20") int count) {
        
        TimelineResponse response = timelineService.getTimelineByImportance(
                principal.getUid(), 
                TimelineService.ImportanceType.EVERYONE, 
                index, 
                count
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/status")
    @Operation(summary = "Post Status", description = "Create a new status update")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Map<String, Object>> postStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody StatusRequest request) {
        
        Post post = shareService.createStatus(principal.getUid(), request);
        
        return ResponseEntity.ok(Map.of(
            "id", post.getId(),
            "type", post.getType(),
            "uid", post.getUid(),
            "content", post.getContent() != null ? post.getContent() : "",
            "url", post.getUrl() != null ? post.getUrl() : "",
            "created", post.getCreated()
        ));
    }
}

