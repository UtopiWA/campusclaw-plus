package com.campusclaw.grading;

import jakarta.validation.constraints.NotBlank;

public record SubmitRequest(@NotBlank String content) {
}
