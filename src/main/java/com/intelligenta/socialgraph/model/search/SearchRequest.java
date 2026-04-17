package com.intelligenta.socialgraph.model.search;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SearchRequest(
    @NotBlank String query,
    @Min(1) @Max(100) Integer limit) {
}
