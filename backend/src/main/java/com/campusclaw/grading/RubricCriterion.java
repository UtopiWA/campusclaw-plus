package com.campusclaw.grading;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RubricCriterion(
        @NotBlank @Size(max = 32) @Pattern(regexp = "[A-Za-z0-9_-]+") String key,
        @NotBlank @Size(max = 500) String description,
        @Min(1) @Max(100) int points) {
}
