package com.camerarrific.socialgraph.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for member listings (followers, following, friends, etc.).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Members list response")
public class MembersResponse {

    @Schema(description = "List of members")
    private List<Member> members;

    @Schema(description = "Total count", example = "150")
    private Integer count;

    @Schema(description = "Request duration in milliseconds", example = "25")
    private Long duration;

    /**
     * Individual member in the list.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Member details")
    public static class Member {

        @Schema(description = "User UUID", example = "550e8400-e29b-41d4-a716-446655440000")
        private String uid;

        @Schema(description = "Username", example = "johndoe")
        private String username;

        @Schema(description = "Full name", example = "John Doe")
        private String fullname;
    }
}

