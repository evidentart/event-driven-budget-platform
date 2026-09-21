package com.sea.expenseservice.dto;

import com.sea.expenseservice.model.ExpenseType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class ExpenseResponse {
    private UUID id;
    private String title;
    private String description;

    private String amount;
    private ExpenseType category;
    private Instant expenseDate;
    private String budgetStatus;
    private String budgetWarning;
    private String remainingBudgetAfter;
}
