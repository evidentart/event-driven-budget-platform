package com.sea.budgetservice.repository;

import com.sea.budgetservice.ai.AiCommandType;
import com.sea.budgetservice.model.AiCommandOutbox;
import com.sea.budgetservice.model.AiCommandOutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class AiCommandOutboxRepositoryQueryTest {

    @Autowired
    private AiCommandOutboxRepository repository;

    private final Instant now = Instant.parse("2026-02-10T12:00:00Z");

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void skipsDelayedAndActivelyLeasedRowsAndReturnsLowestEligibleSequence() {
        AiCommandOutbox delayed = save(AiCommandOutboxStatus.RETRYABLE_FAILURE,
                now.plusSeconds(60), null);
        AiCommandOutbox activeLease = save(AiCommandOutboxStatus.IN_FLIGHT,
                null, now.plusSeconds(60));
        AiCommandOutbox expiredLease = save(AiCommandOutboxStatus.IN_FLIGHT,
                null, now.minusSeconds(1));
        AiCommandOutbox ready = save(AiCommandOutboxStatus.PENDING, now, null);
        AiCommandOutbox laterReady = save(AiCommandOutboxStatus.PENDING, now, null);

        Optional<AiCommandOutbox> first = findEligible();

        assertEquals(expiredLease.getOutboxSequence(), first.orElseThrow().getOutboxSequence());
        assertEquals(true, expiredLease.getOutboxSequence() < ready.getOutboxSequence());
        assertEquals(true, delayed.getOutboxSequence() < ready.getOutboxSequence());
        assertEquals(true, activeLease.getOutboxSequence() < ready.getOutboxSequence());

        repository.delete(expiredLease);
        repository.flush();

        Optional<AiCommandOutbox> next = findEligible();
        assertEquals(ready.getOutboxSequence(), next.orElseThrow().getOutboxSequence());
        assertEquals(true, ready.getOutboxSequence() < laterReady.getOutboxSequence());
    }

    private Optional<AiCommandOutbox> findEligible() {
        List<AiCommandOutbox> eligible = repository.findFirstEligibleOrderByOutboxSequenceAsc(
                EnumSet.of(
                        AiCommandOutboxStatus.PENDING,
                        AiCommandOutboxStatus.IN_FLIGHT,
                        AiCommandOutboxStatus.RETRYABLE_FAILURE),
                AiCommandOutboxStatus.IN_FLIGHT,
                now,
                PageRequest.of(0, 1));
        return eligible.stream().findFirst();
    }

    private AiCommandOutbox save(AiCommandOutboxStatus status, Instant nextAttemptAt, Instant lockedUntil) {
        return repository.saveAndFlush(AiCommandOutbox.builder()
                .commandId(UUID.randomUUID())
                .commandType(AiCommandType.GENERATE_BUDGET_INSIGHT)
                .schemaVersion(1)
                .ownerSubject("alice")
                .expenseId(UUID.randomUUID())
                .requestedAt(now)
                .payload("{}")
                .status(status)
                .attemptCount(0)
                .nextAttemptAt(nextAttemptAt)
                .lockedUntil(lockedUntil)
                .build());
    }
}
