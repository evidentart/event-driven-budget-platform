package com.sea.aiservice.exception;

public class InvalidGeneratedInsightException extends NonRetryableAiCommandException {
    public InvalidGeneratedInsightException(String message) {
        super(message);
    }
}
