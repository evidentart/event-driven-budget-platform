package com.sea.aiservice.service;

import com.sea.aiservice.event.BudgetCalculatedEvent;
import com.sea.aiservice.model.AIInsight;
import com.sea.aiservice.model.ExpenseCategory;
import com.sea.aiservice.model.SeverityLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AIInsightServiceFallbackTest {

    @Mock GeminiClient geminiClient;

    @Test
    void usesDeterministicFallbackWhenGeminiIsUnavailable() {
        UUID expenseId = UUID.randomUUID();
        when(geminiClient.generateText(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());
        AIInsightService service = new AIInsightService(geminiClient);

        AIInsight insight = service.generateInsight(BudgetCalculatedEvent.builder()
                .expenseId(expenseId)
                .ownerSubject("alice")
                .expenseCategory(ExpenseCategory.FOOD)
                .expenseAmountCents(1234)
                .hasBudget(false)
                .build());

        assertEquals(expenseId, insight.getExpenseId());
        assertEquals(SeverityLevel.LOW, insight.getSeverity());
        assertFalse(insight.getBudgetWarnings() == null);
        assertNotNull(insight.getCreatedAt());
    }
}
