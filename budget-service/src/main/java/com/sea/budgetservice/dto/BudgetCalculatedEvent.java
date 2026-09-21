package com.sea.budgetservice.dto;

import com.sea.budgetservice.model.ExpenseCategory;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BudgetCalculatedEvent {

    private UUID expenseId;
    private String ownerSubject;
    private long expenseAmountCents;
    private ExpenseCategory expenseCategory;
    private Long totalBudgetCents;
    private Long usedBudgetCents;
    private Long remainingBudgetCents;
    private BigDecimal percentageUsed;
    private String budgetStatus;
    private String budgetWarning;
    private boolean alertSent;
    private boolean hasBudget;
    private Instant timestamp;
}
