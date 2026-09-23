package com.sea.budgetservice.dto;

/**
 * User-facing budget status derived from percentage used.
 */
public enum BudgetStatus {
    // Under 50% used
    HEALTHY,

    // 50-74% used
    ON_TRACK,

    // 75-89% used
    CAUTION,

    // 90-100% used
    NEAR_LIMIT,

    // Over 100% used
    EXCEEDED,

    // No matching budget exists for the requested period
    NO_BUDGET
}
