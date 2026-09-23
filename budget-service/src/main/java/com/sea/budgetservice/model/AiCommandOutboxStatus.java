package com.sea.budgetservice.model;

public enum AiCommandOutboxStatus {
    PENDING,
    IN_FLIGHT,
    RETRYABLE_FAILURE,
    PUBLISHED
}
