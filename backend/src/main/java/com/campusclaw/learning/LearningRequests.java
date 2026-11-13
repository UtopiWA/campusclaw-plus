package com.campusclaw.learning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

public final class LearningRequests {
    private LearningRequests() { }

    public record PointInput(@NotBlank @Size(max = 40) String code,
                             @NotBlank @Size(max = 100) String name,
                             @NotNull @Size(max = 2000) String description, boolean enabled) { }
    public record PlanInput(@Size(max = 200) String goal,
                            @JsonDeserialize(using = PlanIntegerDeserializer.class) @Min(1) @Max(14) Integer days,
                            @JsonDeserialize(using = PlanIntegerDeserializer.class) @Min(10) @Max(120) Integer dailyMinutes) {
        public PlanInput(String goal) { this(goal, 7, 30); }
        public PlanInput {
            goal = goal == null ? "" : goal.trim();
            days = days == null ? 7 : days;
            dailyMinutes = dailyMinutes == null ? 30 : dailyMinutes;
        }
    }
    public record BindingInput(@NotNull @Size(max = 20) List<@NotNull Long> pointIds) { }
    public record MaterialInput(@NotNull Long materialId) { }
}
