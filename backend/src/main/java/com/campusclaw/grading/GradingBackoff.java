package com.campusclaw.grading;

import com.campusclaw.config.GradingProperties;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/** 指数退避加随机抖动；attempts 是已经发生的执行次数。 */
public final class GradingBackoff {
    private GradingBackoff() {
    }

    public static Instant next(GradingProperties properties, int attempts) {
        long multiplier = (long) Math.pow(properties.backoffMultiplier(), Math.max(0, attempts - 1));
        long raw;
        try {
            raw = Math.multiplyExact(properties.backoffBase().toMillis(), multiplier);
        } catch (ArithmeticException exception) {
            raw = Long.MAX_VALUE;
        }
        long capped = Math.min(raw, properties.backoffMax().toMillis());
        double jitter = 1.0 + ThreadLocalRandom.current().nextDouble() * properties.backoffJitter();
        long delay = Math.max(1L, (long) Math.ceil(capped * jitter));
        return Instant.now().plusMillis(delay);
    }
}
