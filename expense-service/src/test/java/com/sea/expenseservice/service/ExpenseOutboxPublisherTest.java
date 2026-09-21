package com.sea.expenseservice.service;

import com.sea.expenseservice.model.ExpenseOutboxEvent;
import com.sea.expenseservice.model.ExpenseOutboxStatus;
import com.sea.expenseservice.kafka.ExpenseEventType;
import com.sea.expenseservice.repository.ExpenseOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExpenseOutboxPublisherTest {

    @Test
    void successfulPublicationMarksTheEarliestSequencePublished() {
        ExpenseOutboxRepository repository = mock(ExpenseOutboxRepository.class);
        KafkaTemplate<String, byte[]> kafkaTemplate = mock(KafkaTemplate.class);
        ExpenseOutboxEvent event = event(7L);
        when(repository.findFirstByStatusInOrderByOutboxSequenceAsc(anyCollection()))
                .thenReturn(java.util.Optional.of(event));
        when(kafkaTemplate.send(eq("expense"), eq(event.getAggregateId().toString()), any(byte[].class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        ExpenseOutboxPublisher publisher = publisher(repository, kafkaTemplate);
        publisher.publishNext();

        assertEquals(ExpenseOutboxStatus.PUBLISHED, event.getStatus());
        assertEquals(1, event.getAttemptCount());
        assertNull(event.getNextAttemptAt());
        verify(kafkaTemplate).send(eq("expense"), eq(event.getAggregateId().toString()), any(byte[].class));
        verify(repository, times(2)).saveAndFlush(event);
    }

    @Test
    void failedPublicationRemainsRetryableWithoutAnAttemptLimit() {
        ExpenseOutboxRepository repository = mock(ExpenseOutboxRepository.class);
        KafkaTemplate<String, byte[]> kafkaTemplate = mock(KafkaTemplate.class);
        ExpenseOutboxEvent event = event(8L);
        when(repository.findFirstByStatusInOrderByOutboxSequenceAsc(anyCollection()))
                .thenReturn(java.util.Optional.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), any(byte[].class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka unavailable")));

        ExpenseOutboxPublisher publisher = publisher(repository, kafkaTemplate);
        publisher.publishNext();

        assertEquals(ExpenseOutboxStatus.RETRYABLE_FAILURE, event.getStatus());
        assertEquals(1, event.getAttemptCount());
        assertNotNull(event.getNextAttemptAt());
        assertNotEquals(ExpenseOutboxStatus.PUBLISHED, event.getStatus());
    }

    private ExpenseOutboxPublisher publisher(
            ExpenseOutboxRepository repository,
            KafkaTemplate<String, byte[]> kafkaTemplate) {
        ExpenseOutboxPublisher publisher = new ExpenseOutboxPublisher(
                repository,
                kafkaTemplate,
                Clock.fixed(Instant.parse("2026-02-10T12:00:00Z"), ZoneOffset.UTC)
        );
        ReflectionTestUtils.setField(publisher, "expenseTopic", "expense");
        ReflectionTestUtils.setField(publisher, "sendTimeoutMs", 1000L);
        return publisher;
    }

    private ExpenseOutboxEvent event(long sequence) {
        Instant now = Instant.parse("2026-02-10T12:00:00Z");
        return ExpenseOutboxEvent.builder()
                .outboxSequence(sequence)
                .eventId(UUID.randomUUID())
                .eventType(ExpenseEventType.EXPENSE_CREATED)
                .aggregateId(UUID.randomUUID())
                .ownerSubject("alice")
                .payload("{\"eventType\":\"EXPENSE_CREATED\"}")
                .status(ExpenseOutboxStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(now)
                .build();
    }
}
