package com.intelligenta.socialgraph.controller;

import com.intelligenta.socialgraph.model.TimelineResponse;
import com.intelligenta.socialgraph.security.AuthenticatedUser;
import com.intelligenta.socialgraph.service.TimelineService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for timeline endpoints.
 */
@RestController
@RequestMapping("/api")
public class TimelineController {

    private final TimelineService timelineService;

    public TimelineController(TimelineService timelineService) {
        this.timelineService = timelineService;
    }

    /**
     * Get user timeline (FIFO order).
     */
    @GetMapping("/timeline")
    public ResponseEntity<TimelineResponse> getTimeline(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam int index,
            @RequestParam int count) {
        TimelineResponse response = timelineService.getFifoTimeline(user.getUid(), index, count);
        return ResponseEntity.ok(response);
    }

    /**
     * Get timeline sorted by personal importance.
     */
    @GetMapping("/timeline/personal")
    public ResponseEntity<TimelineResponse> getPersonalTimeline(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam int index,
            @RequestParam int count) {
        TimelineResponse response = timelineService.getSocialImportanceTimeline(
            user.getUid(), index, count, TimelineService.Importance.PERSONAL);
        return ResponseEntity.ok(response);
    }

    /**
     * Get timeline sorted by global importance.
     */
    @GetMapping("/timeline/everyone")
    public ResponseEntity<TimelineResponse> getEveryoneTimeline(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam int index,
            @RequestParam int count) {
        TimelineResponse response = timelineService.getSocialImportanceTimeline(
            user.getUid(), index, count, TimelineService.Importance.EVERYONE);
        return ResponseEntity.ok(response);
    }
}
