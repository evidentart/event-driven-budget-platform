package com.sea.aiservice.listener;

import com.sea.aiservice.exception.NonRetryableAiCommandException;
import com.sea.aiservice.service.AIInsightService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiCommandListenerTest {

    private final AIInsightService insightService = mock(AIInsightService.class);
    private final AiCommandListener listener = new AiCommandListener(new ObjectMapper(), insightService);

    @Test
    void routesValidGenerateCommandToInsightService() {
        listener.process(message(validCommand("GENERATE_BUDGET_INSIGHT")));

        verify(insightService).processGenerate(any());
        verify(insightService, never()).processDelete(any());
    }

    @Test
    void routesValidDeleteCommandToInsightService() {
        listener.process(message(validCommand("DELETE_BUDGET_INSIGHT")));

        verify(insightService).processDelete(any());
        verify(insightService, never()).processGenerate(any());
    }

    @Test
    void rejectsMalformedOrUnsupportedCommandsWithoutCallingService() {
        assertThrows(NonRetryableAiCommandException.class,
                () -> listener.process(message("{\"commandId\":\"11111111-1111-1111-1111-111111111111\"}")));
        assertThrows(NonRetryableAiCommandException.class,
                () -> listener.process(message(validCommand("GENERATE_BUDGET_INSIGHT")
                        .replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"))));

        verifyNoInteractions(insightService);
    }

    @Test
    void rejectsGenerateCommandWithInvalidFinancialData() {
        String invalid = validCommand("GENERATE_BUDGET_INSIGHT")
                .replace("\"expenseAmountCents\": 1234", "\"expenseAmountCents\": 0");

        assertThrows(NonRetryableAiCommandException.class,
                () -> listener.process(message(invalid)));
        verifyNoInteractions(insightService);
    }

    private Message message(String body) {
        return new Message(body.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }

    private String validCommand(String type) {
        return """
                {
                  "commandId": "11111111-1111-1111-1111-111111111111",
                  "commandType": "%s",
                  "schemaVersion": 1,
                  "ownerSubject": "alice",
                  "expenseId": "22222222-2222-2222-2222-222222222222",
                  "generation": 1,
                  "sourceExpenseEventId": "33333333-3333-3333-3333-333333333333",
                  "requestedAt": "2026-02-18T18:30:00Z",
                  "expenseAmountCents": 1234,
                  "expenseCategory": "FOOD",
                  "hasBudget": true
                }
                """.formatted(type);
    }
}
