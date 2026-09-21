package com.sea.expenseservice.kafka;

import com.google.protobuf.Timestamp;
import com.sea.expenseservice.model.Expense;
import expense.events.ExpenseCategory;
import expense.events.ExpenseCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseEventProducer {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final Clock clock;

    @Value("${app.kafka.topics.expense-created:expense}")
    private String expenseCreatedTopic;

    public void sendExpenseCreated(Expense expense) {
        Instant createdInstant = safeCreatedInstant(expense.getCreatedDate());

        ExpenseCreatedEvent event = ExpenseCreatedEvent.newBuilder()
                .setExpenseId(expense.getId().toString())
                .setOwnerSubject(expense.getOwnerSubject())
                .setAmountCents(toCents(expense.getAmount()))
                .setCategory(safeCategory(expense.getCategory() != null ? expense.getCategory().name() : null))
                .setCreatedAt(toProtoTimestamp(createdInstant))
                .setExpenseTimestamp(toProtoTimestamp(expense.getExpenseDate()))
                .build();
        // Use a stable key so event ordering is consistent per expense.
        String key = expense.getId().toString();

        kafkaTemplate.send(expenseCreatedTopic, key, event.toByteArray())
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish ExpenseCreatedEvent expenseId={} topic={}: {}",
                                expense.getId(), expenseCreatedTopic, ex.getMessage(), ex);
                        return;
                    }
                    log.info("Published ExpenseCreatedEvent expenseId={} topic={} partition={} offset={}",
                            expense.getId(),
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                });
    }

    private Instant safeCreatedInstant(Instant createdDate) {
        return createdDate != null ? createdDate : Instant.now(clock);
    }

    private static Timestamp toProtoTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    private static ExpenseCategory safeCategory(String name) {
        if (name == null) return ExpenseCategory.CATEGORY_UNSPECIFIED;
        try {
            return ExpenseCategory.valueOf(name);
        } catch (IllegalArgumentException e) {
            return ExpenseCategory.CATEGORY_UNSPECIFIED;
        }
    }

    private static long toCents(BigDecimal amount) {
        if (amount == null) return 0L;
        return amount.setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .longValueExact();
    }
}

