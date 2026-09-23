package com.sea.aiservice.command;

import com.sea.aiservice.model.ExpenseCategory;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
public class AiCommand {
    private UUID commandId;
    private AiCommandType commandType;
    private int schemaVersion;
    private String ownerSubject;
    private UUID expenseId;
    private int generation;
    private UUID sourceExpenseEventId;
    private Instant requestedAt;
    private String accountingPeriod;
    private Long expenseAmountCents;
    private ExpenseCategory expenseCategory;
    private Boolean hasBudget;
    private Long totalBudgetCents;
    private Long usedBudgetCents;
    private Long remainingBudgetCents;
    private BigDecimal percentageUsed;
    private String budgetStatus;
    private String budgetWarning;
    private Boolean alertSent;
}
