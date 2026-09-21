package com.sea.budgetservice.kafka;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.sea.budgetservice.model.ExpenseCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExpenseEventParser {

    private static final int SUPPORTED_SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;

    public ExpenseEventEnvelope parse(byte[] eventBytes) {
        if (eventBytes == null || eventBytes.length == 0) {
            throw new MalformedExpenseEventException("Expense event payload is empty");
        }

        try {
            ExpenseEventEnvelope event = objectMapper.readValue(eventBytes, ExpenseEventEnvelope.class);
            validate(event);
            return event;
        } catch (JacksonException | IllegalArgumentException e) {
            if (e instanceof MalformedExpenseEventException malformed) {
                throw malformed;
            }
            throw new MalformedExpenseEventException("Expense event payload is malformed", e);
        }
    }

    private void validate(ExpenseEventEnvelope event) {
        if (event == null || event.eventId() == null) {
            throw new MalformedExpenseEventException("Expense event is missing eventId");
        }
        if (event.eventType() == null) {
            throw new MalformedExpenseEventException("Expense event is missing eventType");
        }
        if (event.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
            throw new MalformedExpenseEventException("Unsupported expense event schema version");
        }
        if (event.aggregateId() == null) {
            throw new MalformedExpenseEventException("Expense event is missing aggregateId");
        }
        if (event.ownerSubject() == null || event.ownerSubject().isBlank()) {
            throw new MalformedExpenseEventException("Expense event is missing ownerSubject");
        }
        if (event.occurredAt() == null || event.payload() == null || event.payload().expenseTimestamp() == null) {
            throw new MalformedExpenseEventException("Expense event is missing a required timestamp or payload");
        }
        if (event.payload().amountCents() <= 0) {
            throw new MalformedExpenseEventException("Expense event amount must be greater than zero");
        }
        if (event.payload().category() == null || event.payload().category().isBlank()) {
            throw new MalformedExpenseEventException("Expense event is missing category");
        }
        try {
            ExpenseCategory.valueOf(event.payload().category());
        } catch (IllegalArgumentException e) {
            throw new MalformedExpenseEventException("Expense event contains an unknown category", e);
        }
    }
}
