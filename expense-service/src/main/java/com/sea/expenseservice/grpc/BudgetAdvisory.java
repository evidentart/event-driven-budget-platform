package com.sea.expenseservice.grpc;

public record BudgetAdvisory(
        boolean available,
        String status,
        String warning,
        Long remainingCentsAfter
) {

    public static BudgetAdvisory unavailable(String warning) {
        return new BudgetAdvisory(false, "UNAVAILABLE", warning, null);
    }
}
