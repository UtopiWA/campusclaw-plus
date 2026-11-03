package com.campusclaw.grading;

import com.campusclaw.config.GradingProperties;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GradingBackoffTest {

    @Test
    void growsExponentiallyAndCapsWithoutJitter() {
        GradingProperties properties = properties(0.0, Duration.ofSeconds(10));
        Instant before = Instant.now();

        long first = Duration.between(before, GradingBackoff.next(properties, 1)).toMillis();
        long second = Duration.between(before, GradingBackoff.next(properties, 2)).toMillis();
        long capped = Duration.between(before, GradingBackoff.next(properties, 10)).toMillis();

        assertTrue(first >= 1000 && first <= 1100, "first: " + first);
        assertTrue(second >= 2000 && second <= 2100, "second: " + second);
        assertTrue(capped >= 10000 && capped <= 10100, "capped: " + capped);
    }

    @Test
    void jitterStaysWithinConfiguredBounds() {
        GradingProperties properties = properties(0.2, Duration.ofSeconds(5));
        Instant before = Instant.now();
        long delay = Duration.between(before, GradingBackoff.next(properties, 2)).toMillis();
        assertTrue(delay >= 2000 && delay <= 2500, "delay: " + delay);
    }

    private GradingProperties properties(double jitter, Duration max) {
        return new GradingProperties(false, false, false, 5,
                Duration.ofSeconds(1), 2, jitter, max,
                Duration.ofSeconds(90), Duration.ofSeconds(5), 10, Duration.ofSeconds(5),
                "v1", "grading.events", "grading.tasks", "grading.dlq",
                "grading.dispatch", "grading.retry", "grading.dead-letter");
    }
}
