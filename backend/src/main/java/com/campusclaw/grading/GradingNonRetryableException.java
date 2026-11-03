package com.campusclaw.grading;

public class GradingNonRetryableException extends RuntimeException {
    private final String code;

    public GradingNonRetryableException(String code) {
        super(code);
        this.code = code;
    }

    public GradingNonRetryableException(String code, Throwable cause) {
        super(code, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
