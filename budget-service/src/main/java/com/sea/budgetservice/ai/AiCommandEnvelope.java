package com.sea.budgetservice.ai;

import com.sea.budgetservice.model.ExpenseCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AiCommandEnvelope(
        UUID commandId,
        AiCommandType commandType,
        int schemaVersion,
        String ownerSubject,
        UUID expenseId,
        int generation,
        UUID sourceExpenseEventId,
        Instant requestedAt,
        String accountingPeriod,
        Long expenseAmountCents,
        ExpenseCategory expenseCategory,
        Boolean hasBudget,
        Long totalBudgetCents,
        Long usedBudgetCents,
        Long remainingBudgetCents,
        BigDecimal percentageUsed,
        String budgetStatus,
        String budgetWarning,
        Boolean alertSent
) {
}
