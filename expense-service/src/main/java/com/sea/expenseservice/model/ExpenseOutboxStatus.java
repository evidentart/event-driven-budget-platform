package com.sea.expenseservice.model;

public enum ExpenseOutboxStatus {
    PENDING,
    IN_FLIGHT,
    RETRYABLE_FAILURE,
    PUBLISHED
}
