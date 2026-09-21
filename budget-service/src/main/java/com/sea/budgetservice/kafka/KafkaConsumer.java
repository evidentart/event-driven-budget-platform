package com.sea.budgetservice.kafka;

import com.google.protobuf.InvalidProtocolBufferException;
import com.sea.budgetservice.model.ExpenseCategory;
import com.sea.budgetservice.service.BudgetService;
import expense.events.ExpenseCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumer {

    private final BudgetService budgetService;

    @KafkaListener(
            topics = "${app.kafka.topics.expense-created:expense}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeExpenseEvent(byte[] eventBytes, Acknowledgment ack) {
        try {
            ExpenseCreatedEvent event = ExpenseCreatedEvent.parseFrom(eventBytes);

            UUID expenseId = UUID.fromString(event.getExpenseId());
            String ownerSubject = event.getOwnerSubject();
            if (ownerSubject == null || ownerSubject.isBlank()) {
                throw new IllegalArgumentException("ExpenseCreatedEvent is missing owner subject");
            }

            BigDecimal expenseAmount = centsToBigDecimal(event.getAmountCents());
            ExpenseCategory category = mapProtoCategory(event.getCategory());
            String expensePeriod = resolveExpensePeriod(event);

            budgetService.trackExpense(expenseId, ownerSubject, expensePeriod, expenseAmount, category);

            ack.acknowledge();

            log.info("Tracked expense event expenseId={} ownerSubject={} amount={} category={}",
                    expenseId, ownerSubject, expenseAmount, category);

        } catch (InvalidProtocolBufferException e) {
            log.error("Failed to parse ExpenseCreatedEvent. Skipping message.", e);
            ack.acknowledge(); // malformed message should not be retried
        } catch (Exception e) {
            log.error("Error processing expense event. Message will be retried.", e);
        }
    }

    private static BigDecimal centsToBigDecimal(long cents) {
        return BigDecimal.valueOf(cents).movePointLeft(2);
    }

    private static String resolveExpensePeriod(ExpenseCreatedEvent event) {
        String expensePeriod = event.getExpensePeriod();
        if (!expensePeriod.isBlank()) {
            return expensePeriod;
        }
        if (event.hasCreatedAt()) {
            Instant createdAt = Instant.ofEpochSecond(
                    event.getCreatedAt().getSeconds(),
                    event.getCreatedAt().getNanos()
            );
            return YearMonth.from(LocalDateTime.ofInstant(createdAt, ZoneOffset.UTC)).toString();
        }
        return YearMonth.now(ZoneOffset.UTC).toString();
    }

    private ExpenseCategory mapProtoCategory(expense.events.ExpenseCategory protoCategory) {
        if (protoCategory == null || protoCategory == expense.events.ExpenseCategory.CATEGORY_UNSPECIFIED) {
            return ExpenseCategory.OTHER;
        }

        try {
            return ExpenseCategory.valueOf(protoCategory.name());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown category '{}' from event, defaulting to OTHER", protoCategory.name());
            return ExpenseCategory.OTHER;
        }
    }
}

