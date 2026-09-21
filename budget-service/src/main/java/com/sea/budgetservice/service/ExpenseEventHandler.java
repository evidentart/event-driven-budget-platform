package com.sea.budgetservice.service;

import com.sea.budgetservice.kafka.ExpenseEventEnvelope;
import com.sea.budgetservice.kafka.ExpenseEventType;
import com.sea.budgetservice.model.ExpenseCategory;
import com.sea.budgetservice.repository.InboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseEventHandler {

    private final InboxEventRepository inboxEventRepository;
    private final BudgetService budgetService;
    private final Clock clock;

    @Transactional
    public void handle(ExpenseEventEnvelope event) {
        int inserted = inboxEventRepository.insertIfAbsent(
                UUID.randomUUID(),
                event.eventId(),
                event.eventType().name(),
                event.schemaVersion(),
                Instant.now(clock)
        );
        if (inserted == 0) {
            log.info("Ignoring duplicate expense event eventId={} eventType={}",
                    event.eventId(), event.eventType());
            return;
        }

        ExpenseCategory category = ExpenseCategory.valueOf(event.payload().category());
        BigDecimal amount = BigDecimal.valueOf(event.payload().amountCents(), 2);

        if (event.eventType() == ExpenseEventType.EXPENSE_CREATED) {
            budgetService.trackExpense(
                    event.aggregateId(),
                    event.ownerSubject(),
                    event.payload().expenseTimestamp(),
                    amount,
                    category
            );
        } else if (event.eventType() == ExpenseEventType.EXPENSE_DELETED) {
            budgetService.reverseExpense(
                    event.aggregateId(),
                    event.ownerSubject(),
                    event.payload().expenseTimestamp(),
                    amount,
                    category
            );
        }
    }
}
