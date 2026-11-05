package com.campusclaw.grading;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewSubmissionRequest(
        @NotNull @Min(0) Integer score,
        @NotBlank @Size(max = 2000) String comment) { }
