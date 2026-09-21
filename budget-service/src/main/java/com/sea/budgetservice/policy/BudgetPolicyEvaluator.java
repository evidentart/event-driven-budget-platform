package com.sea.budgetservice.policy;

import com.sea.budgetservice.dto.BudgetStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class BudgetPolicyEvaluator {

    public BudgetEvaluation evaluate(BigDecimal budgetLimit, BigDecimal currentSpent, BigDecimal additionalExpense) {
        return evaluate(toCents(budgetLimit, "Budget limit"),
                toCents(currentSpent, "Current spent"),
                toCents(additionalExpense, "Additional expense"));
    }

    public BudgetEvaluation evaluate(long budgetLimitCents, long currentSpentCents, long additionalExpenseCents) {
        if (budgetLimitCents <= 0) {
            throw new IllegalArgumentException("Budget limit must be greater than zero");
        }
        if (currentSpentCents < 0) {
            throw new IllegalArgumentException("Current spent cannot be negative");
        }
        if (additionalExpenseCents < 0) {
            throw new IllegalArgumentException("Additional expense cannot be negative");
        }

        long projectedSpentCents = Math.addExact(currentSpentCents, additionalExpenseCents);
        long remainingCents = Math.subtractExact(budgetLimitCents, projectedSpentCents);
        BigDecimal percentageUsed = BigDecimal.valueOf(projectedSpentCents)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(budgetLimitCents), 4, RoundingMode.HALF_UP);
        BudgetStatus status = statusFor(projectedSpentCents, budgetLimitCents);

        return new BudgetEvaluation(
                true,
                budgetLimitCents,
                currentSpentCents,
                projectedSpentCents,
                remainingCents,
                percentageUsed,
                status,
                warningFor(status, remainingCents)
        );
    }

    public long toCents(BigDecimal amount, String label) {
        if (amount == null || amount.signum() < 0 || amount.scale() > 2) {
            throw new IllegalArgumentException(label + " must be non-negative with at most two decimal places");
        }
        return amount.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    }

    public BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }

    public BigDecimal percentageFor(long amountCents, long budgetLimitCents) {
        if (amountCents < 0 || budgetLimitCents <= 0) {
            throw new IllegalArgumentException("Percentage inputs must be non-negative with a positive budget limit");
        }
        return BigDecimal.valueOf(amountCents)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(budgetLimitCents), 4, RoundingMode.HALF_UP);
    }

    private BudgetStatus statusFor(long projectedSpentCents, long budgetLimitCents) {
        BigDecimal projected = BigDecimal.valueOf(projectedSpentCents);
        BigDecimal limit = BigDecimal.valueOf(budgetLimitCents);
        BigDecimal percentage = projected.multiply(BigDecimal.valueOf(100));

        if (percentage.compareTo(limit.multiply(BigDecimal.valueOf(50))) < 0) return BudgetStatus.HEALTHY;
        if (percentage.compareTo(limit.multiply(BigDecimal.valueOf(75))) < 0) return BudgetStatus.ON_TRACK;
        if (percentage.compareTo(limit.multiply(BigDecimal.valueOf(90))) < 0) return BudgetStatus.CAUTION;
        if (percentage.compareTo(limit.multiply(BigDecimal.valueOf(100))) <= 0) return BudgetStatus.NEAR_LIMIT;
        return BudgetStatus.EXCEEDED;
    }

    private String warningFor(BudgetStatus status, long remainingCents) {
        return switch (status) {
            case HEALTHY, ON_TRACK -> "";
            case CAUTION -> "Caution: spending has reached at least 75% of the monthly budget.";
            case NEAR_LIMIT -> "Near limit: spending has reached at least 90% of the monthly budget.";
            case EXCEEDED -> "Budget exceeded by " + fromCents(Math.abs(remainingCents)).toPlainString() + ".";
            case NO_BUDGET -> "No monthly budget is set for this period.";
        };
    }
}
