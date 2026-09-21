package com.sea.budgetservice.kafka;

import com.google.protobuf.Timestamp;
import com.sea.budgetservice.model.ExpenseCategory;
import com.sea.budgetservice.service.BudgetService;
import expense.events.ExpenseCreatedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class KafkaConsumerTest {

    @Test
    void acknowledgesMalformedEventWhenRequiredExpenseTimestampIsMissing() {
        BudgetService budgetService = mock(BudgetService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        KafkaConsumer consumer = new KafkaConsumer(budgetService);

        consumer.consumeExpenseEvent(ExpenseCreatedEvent.newBuilder()
                .setExpenseId(UUID.randomUUID().toString())
                .setOwnerSubject("alice")
                .setAmountCents(100)
                .setCategory(expense.events.ExpenseCategory.FOOD)
                .setCreatedAt(timestamp("2026-02-10T12:00:00Z"))
                .build().toByteArray(), acknowledgment);

        verify(acknowledgment).acknowledge();
        verifyNoInteractions(budgetService);
    }

    @Test
    void forwardsAbsoluteExpenseTimestampToBudgetTracking() {
        BudgetService budgetService = mock(BudgetService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        KafkaConsumer consumer = new KafkaConsumer(budgetService);
        UUID expenseId = UUID.randomUUID();
        Instant expenseTimestamp = Instant.parse("2026-02-10T12:00:00Z");

        consumer.consumeExpenseEvent(ExpenseCreatedEvent.newBuilder()
                .setExpenseId(expenseId.toString())
                .setOwnerSubject("alice")
                .setAmountCents(1234)
                .setCategory(expense.events.ExpenseCategory.FOOD)
                .setCreatedAt(timestamp("2026-02-10T12:01:00Z"))
                .setExpenseTimestamp(timestamp(expenseTimestamp.toString()))
                .build().toByteArray(), acknowledgment);

        ArgumentCaptor<Instant> timestampCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(budgetService).trackExpense(eq(expenseId), eq("alice"), timestampCaptor.capture(),
                eq(new java.math.BigDecimal("12.34")), eq(ExpenseCategory.FOOD));
        assertEquals(expenseTimestamp, timestampCaptor.getValue());
        verify(acknowledgment).acknowledge();
    }

    private static Timestamp timestamp(String value) {
        Instant instant = Instant.parse(value);
        return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }
}
