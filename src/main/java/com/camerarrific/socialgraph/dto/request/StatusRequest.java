package com.camerarrific.socialgraph.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for posting a status update.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Status update request payload")
public class StatusRequest {

    @NotBlank(message = "Content is required")
    @Size(max = 5000, message = "Content must be less than 5000 characters")
    @Schema(description = "Status content/text", example = "Hello, world!", required = true)
    private String content;

    @Schema(description = "Type of status (text, photo, video)", example = "photo", defaultValue = "text")
    private String type = "text";

    @Schema(description = "URL to media content (for photo/video types)", example = "https://example.com/image.jpg")
    private String url;

    @Schema(description = "MD5 hash of the media file (for deduplication)")
    private String md5;
}

