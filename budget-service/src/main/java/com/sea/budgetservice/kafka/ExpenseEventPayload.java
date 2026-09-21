package com.sea.budgetservice.kafka;

import java.time.Instant;

public record ExpenseEventPayload(
        long amountCents,
        String category,
        Instant expenseTimestamp
) {
}
