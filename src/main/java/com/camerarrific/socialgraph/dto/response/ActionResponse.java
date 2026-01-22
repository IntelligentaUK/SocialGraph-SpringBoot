package com.camerarrific.socialgraph.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for action listings (likes, loves, favs, shares).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Action list response")
public class ActionResponse {

    @Schema(description = "Post object UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String object;

    @Schema(description = "List of actors who performed the action")
    private List<ActionActor> actors;

    @Schema(description = "Total count", example = "42")
    private Integer count;

    @Schema(description = "Request duration in milliseconds", example = "15")
    private Long duration;

    /**
     * Actor who performed an action.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Actor who performed an action")
    public static class ActionActor {

        @Schema(description = "Actor type", example = "person")
        private String type;

        @Schema(description = "Actor UUID", example = "550e8400-e29b-41d4-a716-446655440000")
        private String uuid;

        @Schema(description = "Username", example = "johndoe")
        private String username;

        @Schema(description = "Display name", example = "John Doe")
        private String displayName;
    }
}

