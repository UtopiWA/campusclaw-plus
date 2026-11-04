package com.campusclaw.grading;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateAssignmentRequest(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 4000) String prompt,
        @NotNull @Size(min = 1, max = 10) List<@NotNull @Valid RubricCriterion> criteria) {
}
