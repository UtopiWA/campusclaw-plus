package com.campusclaw.grading;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 固定事件字段，不传作答、提示词或模型正文。 */
public final class GradingLog {
    private static final Logger LOG = LoggerFactory.getLogger(GradingLog.class);

    private GradingLog() {
    }

    public static void event(String event, String taskId, String status, int attempts,
                             long durationMs, String failureCode) {
        var builder = LOG.atInfo()
                .addKeyValue("event", event)
                .addKeyValue("taskId", taskId)
                .addKeyValue("status", status)
                .addKeyValue("attempts", attempts)
                .addKeyValue("durationMs", Math.max(0, durationMs));
        if (failureCode != null && !failureCode.isBlank()) {
            builder.addKeyValue("failureCode", failureCode);
        }
        builder.log(event);
    }
}
