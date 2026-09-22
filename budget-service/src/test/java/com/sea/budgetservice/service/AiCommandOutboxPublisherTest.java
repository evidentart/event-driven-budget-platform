package com.sea.budgetservice.service;

import com.sea.budgetservice.ai.AiCommandType;
import com.sea.budgetservice.model.AiCommandOutbox;
import com.sea.budgetservice.model.AiCommandOutboxStatus;
import com.sea.budgetservice.repository.AiCommandOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiCommandOutboxPublisherTest {

    @Mock
    private AiCommandOutboxRepository repository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private AiCommandOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new AiCommandOutboxPublisher(
                repository,
                rabbitTemplate,
                Clock.fixed(Instant.parse("2026-02-10T12:00:00Z"), ZoneOffset.UTC));
        ReflectionTestUtils.setField(publisher, "exchange", "ai.exchange");
        ReflectionTestUtils.setField(publisher, "generateRoutingKey", "ai.generate");
        ReflectionTestUtils.setField(publisher, "deleteRoutingKey", "ai.delete");
        ReflectionTestUtils.setField(publisher, "sendTimeoutMs", 10L);
    }

    @Test
    void confirmedAndRoutedSendMarksTheSpecificRowPublished() {
        AiCommandOutbox event = event(AiCommandType.GENERATE_BUDGET_INSIGHT, "payload-a");
        when(repository.findFirstEligibleOrderByOutboxSequenceAsc(any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(event));
        confirmSends(true, false);

        publisher.publishNext();

        assertEquals(AiCommandOutboxStatus.PUBLISHED, event.getStatus());
        assertEquals(1, event.getAttemptCount());
        verify(repository, times(2)).saveAndFlush(event);
    }

    @Test
    void negativeAcknowledgementLeavesRowRetryable() {
        AiCommandOutbox event = event(AiCommandType.GENERATE_BUDGET_INSIGHT, "payload-negative");
        when(repository.findFirstEligibleOrderByOutboxSequenceAsc(any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(event));
        confirmSends(false, false);

        publisher.publishNext();

        assertEquals(AiCommandOutboxStatus.RETRYABLE_FAILURE, event.getStatus());
        verify(repository, never()).save(argThat(row -> row.getStatus() == AiCommandOutboxStatus.PUBLISHED));
    }

    @Test
    void confirmationTimeoutLeavesRowRetryable() {
        AiCommandOutbox event = event(AiCommandType.GENERATE_BUDGET_INSIGHT, "payload-timeout");
        when(repository.findFirstEligibleOrderByOutboxSequenceAsc(any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(event));
        doNothing().when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        publisher.publishNext();

        assertEquals(AiCommandOutboxStatus.RETRYABLE_FAILURE, event.getStatus());
        verify(repository, never()).save(argThat(row -> row.getStatus() == AiCommandOutboxStatus.PUBLISHED));
    }

    @Test
    void sendFailureLeavesRowRetryable() {
        AiCommandOutbox event = event(AiCommandType.GENERATE_BUDGET_INSIGHT, "payload-send-failure");
        when(repository.findFirstEligibleOrderByOutboxSequenceAsc(any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(event));
        doThrow(new IllegalStateException("connection down"))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        publisher.publishNext();

        assertEquals(AiCommandOutboxStatus.RETRYABLE_FAILURE, event.getStatus());
        verify(repository, never()).save(argThat(row -> row.getStatus() == AiCommandOutboxStatus.PUBLISHED));
    }

    @Test
    void returnedMessageLeavesRowRetryable() {
        AiCommandOutbox event = event(AiCommandType.GENERATE_BUDGET_INSIGHT, "payload-returned");
        when(repository.findFirstEligibleOrderByOutboxSequenceAsc(any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(event));
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.setReturned(new ReturnedMessage(
                    new Message("payload-returned".getBytes(StandardCharsets.UTF_8)),
                    312, "NO_ROUTE", "ai.exchange", "ai.generate"));
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        publisher.publishNext();

        assertEquals(AiCommandOutboxStatus.RETRYABLE_FAILURE, event.getStatus());
        verify(repository, never()).save(argThat(row -> row.getStatus() == AiCommandOutboxStatus.PUBLISHED));
    }

    @Test
    void eachCorrelationControlsOnlyItsOwnCommandAndPayloadRemainsStable() {
        AiCommandOutbox commandA = event(AiCommandType.GENERATE_BUDGET_INSIGHT, "payload-a");
        AiCommandOutbox commandB = event(AiCommandType.DELETE_BUDGET_INSIGHT, "payload-b");
        String payloadA = commandA.getPayload();
        UUID idA = commandA.getCommandId();
        when(repository.findFirstEligibleOrderByOutboxSequenceAsc(any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(commandA), List.of(commandB));
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            boolean ack = idA.toString().equals(correlationData.getId());
            correlationData.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "negative"));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        publisher.publishNext();
        publisher.publishNext();

        assertEquals(AiCommandOutboxStatus.PUBLISHED, commandA.getStatus());
        assertEquals(AiCommandOutboxStatus.RETRYABLE_FAILURE, commandB.getStatus());
        assertEquals(idA, commandA.getCommandId());
        assertEquals(payloadA, commandA.getPayload());
        ArgumentCaptor<CorrelationData> captor = ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbitTemplate, times(2)).send(anyString(), anyString(), any(Message.class), captor.capture());
        assertEquals(idA.toString(), captor.getAllValues().get(0).getId());
        assertEquals(commandB.getCommandId().toString(), captor.getAllValues().get(1).getId());
    }

    @Test
    void delayedOldestRowDoesNotBlockNewerReadyRow() {
        AiCommandOutbox newerReady = event(AiCommandType.GENERATE_BUDGET_INSIGHT, "newer-payload");
        when(repository.findFirstEligibleOrderByOutboxSequenceAsc(any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(newerReady));
        confirmSends(true, false);

        publisher.publishNext();

        assertEquals(AiCommandOutboxStatus.PUBLISHED, newerReady.getStatus());
        verify(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    private void confirmSends(boolean ack, boolean returned) {
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            if (returned) {
                correlationData.setReturned(new ReturnedMessage(
                        invocation.getArgument(2), 312, "NO_ROUTE", "ai.exchange", "ai.generate"));
            }
            correlationData.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "negative"));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    private AiCommandOutbox event(AiCommandType type, String payload) {
        return AiCommandOutbox.builder()
                .outboxSequence((long) UUID.randomUUID().hashCode())
                .commandId(UUID.randomUUID())
                .commandType(type)
                .schemaVersion(1)
                .ownerSubject("alice")
                .expenseId(UUID.randomUUID())
                .requestedAt(Instant.parse("2026-02-10T12:00:00Z"))
                .payload(payload)
                .status(AiCommandOutboxStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(Instant.parse("2026-02-10T12:00:00Z"))
                .build();
    }
}
