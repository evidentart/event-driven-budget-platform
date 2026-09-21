package com.sea.aiservice.event;

import com.sea.aiservice.model.ExpenseCategory;
import lombok.*;

import java.time.LocalDateTime;
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
    private double expenseAmount;
    private ExpenseCategory expenseCategory;
    private boolean hasBudget;
    private double totalBudget;
    private double usedBudget;
    private double remainingBudget;
    private double percentageUsed;
    private String budgetStatus;
    private String budgetWarning;
    private boolean alertSent;

    private LocalDateTime timestamp;
}
