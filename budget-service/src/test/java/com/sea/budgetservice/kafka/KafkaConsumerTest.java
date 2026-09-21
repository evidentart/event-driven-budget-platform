package com.sea.budgetservice.kafka;

import com.sea.budgetservice.model.ExpenseCategory;
import com.sea.budgetservice.repository.InboxEventRepository;
import com.sea.budgetservice.service.BudgetService;
import com.sea.budgetservice.service.ExpenseEventHandler;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KafkaConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void forwardsCreatedEventWithExactMoneyAndTimestamp() {
        InboxEventRepository inboxRepository = mock(InboxEventRepository.class);
        BudgetService budgetService = mock(BudgetService.class);
        when(inboxRepository.insertIfAbsent(any(), any(), anyString(), eq(1), any())).thenReturn(1);

        ExpenseEventHandler handler = new ExpenseEventHandler(
                inboxRepository,
                budgetService,
                Clock.fixed(Instant.parse("2026-02-10T12:00:00Z"), ZoneOffset.UTC)
        );
        KafkaConsumer consumer = new KafkaConsumer(new ExpenseEventParser(objectMapper), handler);
        UUID expenseId = UUID.randomUUID();
        Instant expenseTimestamp = Instant.parse("2026-02-10T12:00:00Z");

        consumer.consumeExpenseEvent(("""
                {
                  "eventId": "%s",
                  "eventType": "EXPENSE_CREATED",
                  "schemaVersion": 1,
                  "aggregateId": "%s",
                  "ownerSubject": "alice",
                  "occurredAt": "2026-02-10T12:01:00Z",
                  "payload": {
                    "amountCents": 1234,
                    "category": "FOOD",
                    "expenseTimestamp": "%s"
                  }
                }
                """).formatted(UUID.randomUUID(), expenseId, expenseTimestamp).getBytes());

        verify(budgetService).trackExpense(
                eq(expenseId), eq("alice"), eq(expenseTimestamp),
                eq(new BigDecimal("12.34")), eq(ExpenseCategory.FOOD));
    }

    @Test
    void duplicateEventDoesNotMutateBudgetAgain() {
        InboxEventRepository inboxRepository = mock(InboxEventRepository.class);
        BudgetService budgetService = mock(BudgetService.class);
        when(inboxRepository.insertIfAbsent(any(), any(), anyString(), eq(1), any())).thenReturn(0);

        ExpenseEventHandler handler = new ExpenseEventHandler(
                inboxRepository,
                budgetService,
                Clock.systemUTC()
        );
        KafkaConsumer consumer = new KafkaConsumer(new ExpenseEventParser(objectMapper), handler);

        consumer.consumeExpenseEvent(validEventJson().getBytes());

        verifyNoInteractions(budgetService);
    }

    @Test
    void malformedEventIsPropagatedToKafkaErrorHandler() {
        KafkaConsumer consumer = new KafkaConsumer(
                new ExpenseEventParser(objectMapper),
                mock(ExpenseEventHandler.class)
        );

        assertThrows(MalformedExpenseEventException.class,
                () -> consumer.consumeExpenseEvent("{\"eventType\":\"EXPENSE_CREATED\"}".getBytes()));
    }

    @Test
    void deletedEventReversesTheExactExpenseSnapshot() {
        InboxEventRepository inboxRepository = mock(InboxEventRepository.class);
        BudgetService budgetService = mock(BudgetService.class);
        when(inboxRepository.insertIfAbsent(any(), any(), anyString(), eq(1), any())).thenReturn(1);
        ExpenseEventHandler handler = new ExpenseEventHandler(inboxRepository, budgetService, Clock.systemUTC());
        KafkaConsumer consumer = new KafkaConsumer(new ExpenseEventParser(objectMapper), handler);
        UUID expenseId = UUID.randomUUID();
        Instant expenseTimestamp = Instant.parse("2026-02-10T12:00:00Z");

        consumer.consumeExpenseEvent(("""
                {
                  "eventId": "%s",
                  "eventType": "EXPENSE_DELETED",
                  "schemaVersion": 1,
                  "aggregateId": "%s",
                  "ownerSubject": "alice",
                  "occurredAt": "2026-02-11T12:01:00Z",
                  "payload": {
                    "amountCents": 1234,
                    "category": "FOOD",
                    "expenseTimestamp": "%s"
                  }
                }
                """).formatted(UUID.randomUUID(), expenseId, expenseTimestamp).getBytes());

        verify(budgetService).reverseExpense(
                eq(expenseId), eq("alice"), eq(expenseTimestamp),
                eq(new BigDecimal("12.34")), eq(ExpenseCategory.FOOD));
    }

    private String validEventJson() {
        return """
                {
                  "eventId": "%s",
                  "eventType": "EXPENSE_CREATED",
                  "schemaVersion": 1,
                  "aggregateId": "%s",
                  "ownerSubject": "alice",
                  "occurredAt": "2026-02-10T12:01:00Z",
                  "payload": {
                    "amountCents": 1234,
                    "category": "FOOD",
                    "expenseTimestamp": "2026-02-10T12:00:00Z"
                  }
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());
    }
}
