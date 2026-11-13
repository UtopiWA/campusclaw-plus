package com.campusclaw.learning;

import com.campusclaw.grading.CreateAssignmentRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class QuestionBankRequests {
    private QuestionBankRequests() { }

    public record Question(@NotBlank @Size(max = 120) String title, @NotBlank @Size(max = 4000) String prompt,
                           @Min(1) @Max(100) int maxScore,
                           @NotEmpty @Size(max = 5) List<@NotNull Long> rubricMaterialIds, boolean enabled) {
        CreateAssignmentRequest assignment() { return new CreateAssignmentRequest(title, prompt, null, maxScore, rubricMaterialIds); }
    }
    public record Availability(boolean enabled) { }
}
