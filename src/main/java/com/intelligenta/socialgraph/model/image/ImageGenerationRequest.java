package com.intelligenta.socialgraph.model.image;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/images/generate}. Only {@code prompt} is
 * required; the rest default to the active provider's defaults.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImageGenerationRequest(
    @NotBlank String prompt,
    String size,
    String style,
    @Min(1) @Max(4) Integer n,
    String responseFormat) {
}
