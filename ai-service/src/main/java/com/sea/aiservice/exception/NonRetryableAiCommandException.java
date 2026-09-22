package com.sea.aiservice.exception;

public class NonRetryableAiCommandException extends RuntimeException {
    public NonRetryableAiCommandException(String message) {
        super(message);
    }

    public NonRetryableAiCommandException(String message, Throwable cause) {
        super(message, cause);
    }
}
