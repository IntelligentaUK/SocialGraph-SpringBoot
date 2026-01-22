package com.camerarrific.socialgraph.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for social actions (like, love, fav, share).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Social action request payload")
public class ActionRequest {

    @NotBlank(message = "Post UUID is required")
    @Schema(description = "UUID of the post to perform action on", example = "550e8400-e29b-41d4-a716-446655440000", required = true)
    private String uuid;
}

