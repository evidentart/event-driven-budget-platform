package com.sea.budgetservice.service;

import com.sea.budgetservice.dto.BudgetCalculatedEvent;
import com.sea.budgetservice.model.ExpenseCategory;
import com.sea.budgetservice.policy.AccountingPeriodResolver;
import com.sea.budgetservice.policy.BudgetPolicyEvaluator;
import com.sea.budgetservice.repository.BudgetRepository;
import com.sea.budgetservice.repository.CategorySpendingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BudgetServiceNoBudgetTest {

    @Mock BudgetRepository budgetRepository;
    @Mock CategorySpendingRepository categorySpendingRepository;
    @Mock RabbitTemplate rabbitTemplate;

    private BudgetService budgetService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-02-10T12:00:00Z"), ZoneOffset.UTC);
        AccountingPeriodResolver resolver = new AccountingPeriodResolver(clock, ZoneOffset.UTC);
        budgetService = new BudgetService(
                budgetRepository, categorySpendingRepository, rabbitTemplate,
                new BudgetPolicyEvaluator(), resolver, clock);
        ReflectionTestUtils.setField(budgetService, "rabbitExchange", "budget.exchange");
        ReflectionTestUtils.setField(budgetService, "rabbitRoutingKey", "budget.calculated");
    }

    @Test
    void missingBudgetPublishesNoBudgetWithoutCreatingSyntheticRow() {
        when(budgetRepository.findByOwnerSubjectAndPeriod("alice", "2026-02"))
                .thenReturn(Optional.empty());

        budgetService.trackExpense(UUID.randomUUID(), "alice",
                Instant.parse("2026-02-10T12:00:00Z"), new BigDecimal("12.34"), ExpenseCategory.FOOD);

        verify(budgetRepository, never()).save(any());
        verify(categorySpendingRepository, never()).save(any());

        ArgumentCaptor<BudgetCalculatedEvent> eventCaptor = ArgumentCaptor.forClass(BudgetCalculatedEvent.class);
        verify(rabbitTemplate).convertAndSend(eq("budget.exchange"), eq("budget.calculated"), eventCaptor.capture());
        assertFalse(eventCaptor.getValue().isHasBudget());
        assertEquals("NO_BUDGET", eventCaptor.getValue().getBudgetStatus());
    }
}
