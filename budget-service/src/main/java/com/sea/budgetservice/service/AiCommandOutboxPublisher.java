package com.sea.budgetservice.service;

import com.sea.budgetservice.model.AiCommandOutbox;
import com.sea.budgetservice.model.AiCommandOutboxStatus;
import com.sea.budgetservice.repository.AiCommandOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiCommandOutboxPublisher {

    private static final long INITIAL_RETRY_DELAY_MS = 1_000L;
    private static final long MAX_RETRY_DELAY_MS = 15 * 60 * 1_000L;
    private static final long LEASE_MS = 60_000L;

    private final AiCommandOutboxRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;

    @Value("${app.rabbitmq.exchange:ai.commands.v1.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.generate-routing-key:ai.insight.generate.v1}")
    private String generateRoutingKey;

    @Value("${app.rabbitmq.delete-routing-key:ai.insight.delete.v1}")
    private String deleteRoutingKey;

    @Value("${app.rabbitmq.outbox.send-timeout-ms:10000}")
    private long sendTimeoutMs;

    @Scheduled(fixedDelayString = "${app.rabbitmq.outbox.poll-delay-ms:1000}")
    public void publishNext() {
        repository.findFirstEligibleOrderByOutboxSequenceAsc(
                        EnumSet.of(
                                AiCommandOutboxStatus.PENDING,
                                AiCommandOutboxStatus.IN_FLIGHT,
                                AiCommandOutboxStatus.RETRYABLE_FAILURE),
                        AiCommandOutboxStatus.IN_FLIGHT,
                        Instant.now(clock),
                        PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .ifPresent(this::publish);
    }

    private void publish(AiCommandOutbox event) {
        Instant now = Instant.now(clock);
        event.setStatus(AiCommandOutboxStatus.IN_FLIGHT);
        event.setAttemptCount(event.getAttemptCount() + 1);
        event.setLockedUntil(now.plusMillis(LEASE_MS));
        event.setLastError(null);
        repository.saveAndFlush(event);

        String routingKey = event.getCommandType().name().equals("DELETE_BUDGET_INSIGHT")
                ? deleteRoutingKey : generateRoutingKey;
        CorrelationData correlationData = new CorrelationData(event.getCommandId().toString());
        Message message = MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(event.getCommandId().toString())
                .setHeader("commandType", event.getCommandType().name())
                .build();

        try {
            rabbitTemplate.send(exchange, routingKey, message, correlationData);
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(sendTimeoutMs, TimeUnit.MILLISECONDS);
            if (confirm == null || !confirm.ack()) {
                throw new IllegalStateException("Rabbit publisher confirmation was negative");
            }
            if (correlationData.getReturned() != null) {
                throw new IllegalStateException("Rabbit message was returned as unroutable: "
                        + correlationData.getReturned());
            }

            event.setStatus(AiCommandOutboxStatus.PUBLISHED);
            event.setPublishedAt(Instant.now(clock));
            event.setLockedUntil(null);
            event.setNextAttemptAt(null);
            repository.saveAndFlush(event);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markRetryable(event, e);
        } catch (Exception e) {
            markRetryable(event, e);
        }
    }

    private void markRetryable(AiCommandOutbox event, Exception failure) {
        long delay = retryDelayMillis(event.getAttemptCount());
        event.setStatus(AiCommandOutboxStatus.RETRYABLE_FAILURE);
        event.setNextAttemptAt(Instant.now(clock).plusMillis(delay));
        event.setLockedUntil(null);
        event.setLastError(errorMessage(failure));
        repository.saveAndFlush(event);
        log.error("Failed to publish AI command commandId={} type={} attempt={} retryInMs={}",
                event.getCommandId(), event.getCommandType(), event.getAttemptCount(), delay, failure);
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
        if (message == null || message.isBlank()) message = failure.getClass().getSimpleName();
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}
