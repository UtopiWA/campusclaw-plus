package com.campusclaw.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 作业批改队列、重试和 worker 行为；取值由服务端配置注入，不写入代码。 */
@Validated
@ConfigurationProperties(prefix = "app.grading")
public record GradingProperties(
        boolean workerEnabled,
        boolean publisherEnabled,
        boolean recoveryEnabled,
        @Min(1) int maxAttempts,
        @NotNull Duration backoffBase,
        @Min(2) int backoffMultiplier,
        @DecimalMin("0.0") @DecimalMax("1.0") double backoffJitter,
        @NotNull Duration backoffMax,
        @NotNull Duration staleRunningTimeout,
        @NotNull Duration outboxRetryDelay,
        @Min(1) int outboxBatchSize,
        @NotNull Duration confirmTimeout,
        @NotBlank String promptVersion,
        @NotBlank String exchange,
        @NotBlank String taskQueue,
        @NotBlank String deadLetterQueue,
        @NotBlank String dispatchRoutingKey,
        @NotBlank String retryRoutingKey,
        @NotBlank String deadLetterRoutingKey) {

    @AssertTrue(message = "Grading durations must be positive and backoff max must cover the base")
    public boolean isTimingValid() {
        if (backoffBase == null || backoffMax == null || staleRunningTimeout == null
                || outboxRetryDelay == null || confirmTimeout == null) return true;
        return backoffBase.toMillis() > 0 && backoffMax.compareTo(backoffBase) >= 0
                && staleRunningTimeout.toMillis() > 0 && outboxRetryDelay.toMillis() > 0
                && confirmTimeout.toMillis() > 0;
    }
}
