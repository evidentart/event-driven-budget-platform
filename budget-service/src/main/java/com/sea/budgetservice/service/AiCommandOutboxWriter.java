package com.sea.budgetservice.service;

import com.sea.budgetservice.ai.AiCommandEnvelope;
import com.sea.budgetservice.ai.AiCommandType;
import com.sea.budgetservice.model.AiCommandOutbox;
import com.sea.budgetservice.model.AiCommandOutboxStatus;
import com.sea.budgetservice.model.ExpenseCategory;
import com.sea.budgetservice.repository.AiCommandOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiCommandOutboxWriter {

    private static final int SCHEMA_VERSION = 1;
    private static final int GENERATION = 1;

    private final AiCommandOutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public void enqueueGeneration(
            UUID expenseId,
            String ownerSubject,
            UUID sourceExpenseEventId,
            String accountingPeriod,
            long expenseAmountCents,
            ExpenseCategory expenseCategory,
            boolean hasBudget,
            Long totalBudgetCents,
            Long usedBudgetCents,
            Long remainingBudgetCents,
            BigDecimal percentageUsed,
            String budgetStatus,
            String budgetWarning,
            boolean alertSent
    ) {
        Instant requestedAt = Instant.now(clock);
        write(new AiCommandEnvelope(
                UUID.randomUUID(), AiCommandType.GENERATE_BUDGET_INSIGHT, SCHEMA_VERSION,
                ownerSubject, expenseId, GENERATION, sourceExpenseEventId, requestedAt,
                accountingPeriod, expenseAmountCents, expenseCategory, hasBudget,
                totalBudgetCents, usedBudgetCents, remainingBudgetCents, percentageUsed,
                budgetStatus, budgetWarning, alertSent
        ));
    }

    public void enqueueDeletion(UUID expenseId, String ownerSubject, UUID sourceExpenseEventId,
                                String accountingPeriod) {
        Instant requestedAt = Instant.now(clock);
        write(new AiCommandEnvelope(
                UUID.randomUUID(), AiCommandType.DELETE_BUDGET_INSIGHT, SCHEMA_VERSION,
                ownerSubject, expenseId, GENERATION, sourceExpenseEventId, requestedAt,
                accountingPeriod, null, null, null, null, null, null, null,
                null, null, null
        ));
    }

    private void write(AiCommandEnvelope command) {
        try {
            repository.save(AiCommandOutbox.builder()
                    .commandId(command.commandId())
                    .commandType(command.commandType())
                    .schemaVersion(command.schemaVersion())
                    .ownerSubject(command.ownerSubject())
                    .expenseId(command.expenseId())
                    .requestedAt(command.requestedAt())
                    .payload(objectMapper.writeValueAsString(command))
                    .status(AiCommandOutboxStatus.PENDING)
                    .attemptCount(0)
                    .nextAttemptAt(command.requestedAt())
                    .build());
        } catch (JacksonException e) {
            throw new IllegalStateException("Unable to serialize AI command", e);
        }
    }
}
