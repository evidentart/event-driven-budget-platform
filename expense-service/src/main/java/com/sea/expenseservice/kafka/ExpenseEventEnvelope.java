package com.sea.expenseservice.kafka;

import java.time.Instant;
import java.util.UUID;

public record ExpenseEventEnvelope(
        UUID eventId,
        ExpenseEventType eventType,
        int schemaVersion,
        UUID aggregateId,
        String ownerSubject,
        Instant occurredAt,
        ExpenseEventPayload payload
) {
}
