package com.sea.aiservice.exception;

public class RetryableAiProcessingException extends RuntimeException {
    public RetryableAiProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    public RetryableAiProcessingException(String message) {
        super(message);
    }
}
