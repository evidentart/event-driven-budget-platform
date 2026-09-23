package com.sea.budgetservice.policy;

import com.sea.budgetservice.dto.BudgetStatus;

import java.math.BigDecimal;

public record BudgetEvaluation(
        boolean budgetExists,
        Long budgetLimitCents,
        Long currentSpentCents,
        Long projectedSpentCents,
        Long remainingCentsAfter,
        BigDecimal percentageUsed,
        BudgetStatus status,
        String warning
) {

    public static BudgetEvaluation noBudget() {
        return new BudgetEvaluation(false, null, null, null, null, null,
                BudgetStatus.NO_BUDGET, "No monthly budget is set for this period.");
    }
}
