package com.sea.expenseservice.exception;

import java.util.UUID;

public class ExpenseNotFoundException extends RuntimeException {
    public ExpenseNotFoundException(UUID expenseId) {
        super("Expense not found with ID: " + expenseId);
    }
}
