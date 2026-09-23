package com.sea.expenseservice.service;

import com.sea.expenseservice.model.Expense;
import com.sea.expenseservice.model.ExpenseOutboxEvent;
import com.sea.expenseservice.model.ExpenseType;
import com.sea.expenseservice.repository.ExpenseOutboxRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class ExpenseOutboxWriterTest {

    @Test
    void writesCreatedEventWithExactCentsAndAbsoluteTimestamp() {
        ExpenseOutboxRepository repository = mock(ExpenseOutboxRepository.class);
        ExpenseOutboxWriter writer = new ExpenseOutboxWriter(
                repository,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-02-10T12:00:00Z"), ZoneOffset.UTC)
        );
        Expense expense = expense();

        writer.enqueueCreated(expense);

        ArgumentCaptor<ExpenseOutboxEvent> captor = ArgumentCaptor.forClass(ExpenseOutboxEvent.class);
        verify(repository).save(captor.capture());
        ExpenseOutboxEvent event = captor.getValue();
        assertTrue(event.getPayload().contains("\"amountCents\":1234"));
        assertTrue(event.getPayload().contains("\"expenseTimestamp\":\"2026-02-10T10:30:00Z\""));
        assertTrue(event.getPayload().contains("\"eventType\":\"EXPENSE_CREATED\""));
    }

    @Test
    void writesDeleteSnapshotBeforeTheExpenseCanBeRemoved() {
        ExpenseOutboxRepository repository = mock(ExpenseOutboxRepository.class);
        ExpenseOutboxWriter writer = new ExpenseOutboxWriter(
                repository,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-02-11T12:00:00Z"), ZoneOffset.UTC)
        );

        writer.enqueueDeleted(expense());

        ArgumentCaptor<ExpenseOutboxEvent> captor = ArgumentCaptor.forClass(ExpenseOutboxEvent.class);
        verify(repository).save(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("\"eventType\":\"EXPENSE_DELETED\""));
    }

    private Expense expense() {
        Expense expense = new Expense();
        expense.setId(UUID.randomUUID());
        expense.setOwnerSubject("alice");
        expense.setAmount(new BigDecimal("12.34"));
        expense.setCategory(ExpenseType.FOOD);
        expense.setExpenseDate(Instant.parse("2026-02-10T10:30:00Z"));
        return expense;
    }
}
