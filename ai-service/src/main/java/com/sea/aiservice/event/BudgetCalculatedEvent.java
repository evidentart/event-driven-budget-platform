package com.sea.aiservice.event;

import com.sea.aiservice.model.ExpenseCategory;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted by budget-service and consumed by ai-service to generate insights.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BudgetCalculatedEvent {

    private UUID expenseId;
    private String ownerSubject;
    private long expenseAmountCents;
    private ExpenseCategory expenseCategory;
    private boolean hasBudget;
    private Long totalBudgetCents;
    private Long usedBudgetCents;
    private Long remainingBudgetCents;
    private BigDecimal percentageUsed;
    private String budgetStatus;
    private String budgetWarning;
    private boolean alertSent;

    private Instant timestamp;
}
