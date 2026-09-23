package com.sea.expenseservice.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.sea.expenseservice.kafka.ExpenseEventEnvelope;
import com.sea.expenseservice.kafka.ExpenseEventPayload;
import com.sea.expenseservice.kafka.ExpenseEventType;
import com.sea.expenseservice.model.Expense;
import com.sea.expenseservice.model.ExpenseOutboxEvent;
import com.sea.expenseservice.model.ExpenseOutboxStatus;
import com.sea.expenseservice.repository.ExpenseOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpenseOutboxWriter {

    private static final int SCHEMA_VERSION = 1;

    private final ExpenseOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public void enqueueCreated(Expense expense) {
        enqueue(expense, ExpenseEventType.EXPENSE_CREATED);
    }

    public void enqueueDeleted(Expense expense) {
        enqueue(expense, ExpenseEventType.EXPENSE_DELETED);
    }

    private void enqueue(Expense expense, ExpenseEventType eventType) {
        if (expense.getId() == null || expense.getOwnerSubject() == null || expense.getExpenseDate() == null
                || expense.getAmount() == null || expense.getCategory() == null) {
            throw new IllegalArgumentException("Cannot create an expense event from an incomplete expense");
        }

        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now(clock);
        ExpenseEventEnvelope envelope = new ExpenseEventEnvelope(
                eventId,
                eventType,
                SCHEMA_VERSION,
                expense.getId(),
                expense.getOwnerSubject(),
                occurredAt,
                new ExpenseEventPayload(
                        expense.getAmount().setScale(2, RoundingMode.UNNECESSARY)
                                .movePointRight(2)
                                .longValueExact(),
                        expense.getCategory().name(),
                        expense.getExpenseDate()
                )
        );

        try {
            String payload = objectMapper.writeValueAsString(envelope);
            outboxRepository.save(ExpenseOutboxEvent.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .schemaVersion(SCHEMA_VERSION)
                    .aggregateId(expense.getId())
                    .ownerSubject(expense.getOwnerSubject())
                    .occurredAt(occurredAt)
                    .payload(payload)
                    .status(ExpenseOutboxStatus.PENDING)
                    .attemptCount(0)
                    .nextAttemptAt(occurredAt)
                    .build());
        } catch (JacksonException e) {
            throw new IllegalStateException("Unable to serialize expense event", e);
        }
    }
}
