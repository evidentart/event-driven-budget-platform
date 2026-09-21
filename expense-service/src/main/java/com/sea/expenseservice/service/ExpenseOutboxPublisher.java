package com.sea.expenseservice.service;

import com.sea.expenseservice.model.ExpenseOutboxEvent;
import com.sea.expenseservice.model.ExpenseOutboxStatus;
import com.sea.expenseservice.repository.ExpenseOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseOutboxPublisher {

    private static final long INITIAL_RETRY_DELAY_MS = 1_000L;
    private static final long MAX_RETRY_DELAY_MS = 15 * 60 * 1_000L;
    private static final long LEASE_MS = 60_000L;

    private final ExpenseOutboxRepository outboxRepository;
    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final Clock clock;

    @Value("${app.kafka.topics.expense-created:expense}")
    private String expenseTopic;

    @Value("${app.kafka.outbox.send-timeout-ms:30000}")
    private long sendTimeoutMs;

    @Scheduled(fixedDelayString = "${app.kafka.outbox.poll-delay-ms:1000}")
    public void publishNext() {
        outboxRepository.findFirstByStatusInOrderByOutboxSequenceAsc(
                        EnumSet.of(
                                ExpenseOutboxStatus.PENDING,
                                ExpenseOutboxStatus.IN_FLIGHT,
                                ExpenseOutboxStatus.RETRYABLE_FAILURE
                        ))
                .filter(this::isReady)
                .ifPresent(this::publish);
    }

    private boolean isReady(ExpenseOutboxEvent event) {
        Instant now = Instant.now(clock);
        if (event.getStatus() == ExpenseOutboxStatus.IN_FLIGHT) {
            return event.getLockedUntil() == null || !event.getLockedUntil().isAfter(now);
        }
        return event.getNextAttemptAt() == null || !event.getNextAttemptAt().isAfter(now);
    }

    private void publish(ExpenseOutboxEvent event) {
        Instant now = Instant.now(clock);
        event.setStatus(ExpenseOutboxStatus.IN_FLIGHT);
        event.setAttemptCount(event.getAttemptCount() + 1);
        event.setLockedUntil(now.plusMillis(LEASE_MS));
        event.setLastError(null);
        outboxRepository.saveAndFlush(event);

        try {
            kafkaTemplate.send(expenseTopic, event.getAggregateId().toString(),
                            event.getPayload().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .get(sendTimeoutMs, TimeUnit.MILLISECONDS);

            event.setStatus(ExpenseOutboxStatus.PUBLISHED);
            event.setPublishedAt(Instant.now(clock));
            event.setLockedUntil(null);
            event.setNextAttemptAt(null);
            outboxRepository.saveAndFlush(event);

            log.info("Published expense event eventId={} eventType={} aggregateId={} sequence={}",
                    event.getEventId(), event.getEventType(), event.getAggregateId(), event.getOutboxSequence());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markRetryable(event, e);
        } catch (Exception e) {
            markRetryable(event, e);
        }
    }

    private void markRetryable(ExpenseOutboxEvent event, Exception failure) {
        long delay = retryDelayMillis(event.getAttemptCount());
        event.setStatus(ExpenseOutboxStatus.RETRYABLE_FAILURE);
        event.setNextAttemptAt(Instant.now(clock).plusMillis(delay));
        event.setLockedUntil(null);
        event.setLastError(errorMessage(failure));
        outboxRepository.saveAndFlush(event);

        log.error("Failed to publish expense event eventId={} eventType={} sequence={} attempt={} retryInMs={}",
                event.getEventId(), event.getEventType(), event.getOutboxSequence(),
                event.getAttemptCount(), delay, failure);
    }

    private long retryDelayMillis(int attempt) {
        long exponent = Math.min(Math.max(attempt - 1L, 0L), 30L);
        long delay;
        try {
            delay = Math.multiplyExact(INITIAL_RETRY_DELAY_MS, 1L << exponent);
        } catch (ArithmeticException e) {
            delay = MAX_RETRY_DELAY_MS;
        }
        return Math.min(delay, MAX_RETRY_DELAY_MS);
    }

    private String errorMessage(Exception failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            message = failure.getClass().getSimpleName();
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}
