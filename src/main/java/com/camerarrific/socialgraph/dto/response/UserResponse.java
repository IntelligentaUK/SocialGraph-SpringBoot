package com.camerarrific.socialgraph.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for user information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User information response")
public class UserResponse {

    @Schema(description = "User's unique identifier", example = "550e8400-e29b-41d4-a716-446655440000")
    private String uid;

    @Schema(description = "Username", example = "johndoe")
    private String username;

    @Schema(description = "Full name", example = "John Doe")
    private String fullname;

    @Schema(description = "Email address", example = "john@example.com")
    private String email;

    @Schema(description = "Number of followers", example = "150")
    private Long followers;

    @Schema(description = "Number of users being followed", example = "75")
    private Long following;

    @Schema(description = "Profile picture URL")
    private String profilePicture;
}

