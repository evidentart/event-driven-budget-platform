package com.sea.budgetservice.dto;

import com.sea.budgetservice.model.ExpenseCategory;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BudgetCalculatedEvent {

    private UUID expenseId;
    private UUID userId;
    private double expenseAmount;
    private ExpenseCategory expenseCategory;
    private double totalBudget;
    private double usedBudget;
    private double remainingBudget;
    private double percentageUsed;
    private String budgetStatus;
    private String budgetWarning;
    private boolean alertSent;
    private boolean hasBudget;
    private LocalDateTime timestamp;
}
