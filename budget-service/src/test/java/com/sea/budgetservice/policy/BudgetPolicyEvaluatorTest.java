package com.sea.budgetservice.policy;

import com.sea.budgetservice.dto.BudgetStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class BudgetPolicyEvaluatorTest {

    private final BudgetPolicyEvaluator evaluator = new BudgetPolicyEvaluator();

    @Test
    void evaluatesExactCentsAndThresholdsWithoutFloatingPointDrift() {
        BudgetEvaluation evaluation = evaluator.evaluate(
                new BigDecimal("100.00"),
                new BigDecimal("49.99"),
                new BigDecimal("0.01"));

        assertEquals(5000L, evaluation.projectedSpentCents());
        assertEquals(5000L, evaluation.remainingCentsAfter());
        assertEquals(new BigDecimal("50.0000"), evaluation.percentageUsed());
        assertEquals(BudgetStatus.ON_TRACK, evaluation.status());
    }

    @Test
    void rejectsZeroNegativeAndOverPrecisionBudgetValues() {
        assertThrows(IllegalArgumentException.class,
                () -> evaluator.evaluate(new BigDecimal("0.00"), BigDecimal.ZERO, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
                () -> evaluator.evaluate(new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("1.001")));
        assertThrows(IllegalArgumentException.class,
                () -> evaluator.evaluate(new BigDecimal("100.00"), new BigDecimal("-0.01"), BigDecimal.ZERO));
    }

    @Test
    void reportsNoBudgetAsAnExplicitState() {
        BudgetEvaluation evaluation = BudgetEvaluation.noBudget();

        assertFalse(evaluation.budgetExists());
        assertEquals(BudgetStatus.NO_BUDGET, evaluation.status());
        assertNull(evaluation.budgetLimitCents());
    }
}
