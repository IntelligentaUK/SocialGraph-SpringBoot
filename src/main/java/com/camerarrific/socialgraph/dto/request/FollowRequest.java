package com.camerarrific.socialgraph.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for follow/unfollow operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Follow/Unfollow request payload")
public class FollowRequest {

    @Schema(description = "User ID to follow/unfollow", example = "550e8400-e29b-41d4-a716-446655440000")
    private String uid;

    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Schema(description = "Username to follow/unfollow (alternative to uid)", example = "johndoe")
    private String username;
}

