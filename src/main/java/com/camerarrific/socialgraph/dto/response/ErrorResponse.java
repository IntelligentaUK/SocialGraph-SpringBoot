package com.camerarrific.socialgraph.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Standard error response DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Error response")
public class ErrorResponse {

    @Schema(description = "Error code", example = "invalid_request")
    private String error;

    @Schema(description = "Human-readable error description", example = "The request was invalid")
    private String errorDescription;

    @Schema(description = "Timestamp of the error")
    @Builder.Default
    private Instant timestamp = Instant.now();

    @Schema(description = "Request path that caused the error", example = "/api/follow")
    private String path;

    @Schema(description = "Validation errors (if applicable)")
    private List<FieldError> errors;

    /**
     * Field-level validation error.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Field validation error")
    public static class FieldError {

        @Schema(description = "Field name", example = "username")
        private String field;

        @Schema(description = "Error message", example = "Username is required")
        private String message;

        @Schema(description = "Rejected value", example = "")
        private Object rejectedValue;
    }
}

