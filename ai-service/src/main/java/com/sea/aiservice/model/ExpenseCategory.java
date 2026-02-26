package com.sea.aiservice.model;

/**
 * Expense categories stored in MongoDB.
 * Existing values must not be removed to avoid breaking persisted data.
 */
public enum ExpenseCategory {
    FOOD,
    TRANSPORT,
    ENTERTAINMENT,
    SHOPPING,
    UTILITIES,
    HEALTH,
    EDUCATION,
    TRAVEL,
    OTHER,
    RENT
}
