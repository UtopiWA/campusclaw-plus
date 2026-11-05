package com.campusclaw.grading;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.util.List;

public record CreateAssignmentRequest(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 4000) String prompt,
        @Size(min = 1, max = 10) List<@NotNull @Valid RubricCriterion> criteria,
        @Min(1) @Max(100) Integer maxScore,
        @Size(max = 5) List<@NotNull Long> rubricMaterialIds) {
    public CreateAssignmentRequest(String title, String prompt, List<RubricCriterion> criteria) {
        this(title, prompt, criteria, null, null);
    }
}
