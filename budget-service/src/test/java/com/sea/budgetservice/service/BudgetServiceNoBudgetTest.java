package com.sea.budgetservice.service;

import com.sea.budgetservice.model.ExpenseCategory;
import com.sea.budgetservice.policy.AccountingPeriodResolver;
import com.sea.budgetservice.policy.BudgetPolicyEvaluator;
import com.sea.budgetservice.repository.BudgetRepository;
import com.sea.budgetservice.repository.CategorySpendingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    @Mock AiCommandOutboxWriter aiCommandOutboxWriter;

    private BudgetService budgetService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-02-10T12:00:00Z"), ZoneOffset.UTC);
        AccountingPeriodResolver resolver = new AccountingPeriodResolver(clock, ZoneOffset.UTC);
        budgetService = new BudgetService(
                budgetRepository, categorySpendingRepository, aiCommandOutboxWriter,
                new BudgetPolicyEvaluator(), resolver);
    }

    @Test
    void missingBudgetPublishesNoBudgetWithoutCreatingSyntheticRow() {
        when(budgetRepository.findByOwnerSubjectAndPeriodForUpdate("alice", "2026-02"))
                .thenReturn(Optional.empty());

        budgetService.trackExpense(UUID.randomUUID(), "alice",
                Instant.parse("2026-02-10T12:00:00Z"), new BigDecimal("12.34"), ExpenseCategory.FOOD);

        verify(budgetRepository, never()).save(any());
        verify(categorySpendingRepository, never()).save(any());

        verify(aiCommandOutboxWriter).enqueueGeneration(
                any(), eq("alice"), any(), eq("2026-02"), eq(1234L), eq(ExpenseCategory.FOOD),
                eq(false), isNull(), isNull(), isNull(), isNull(), eq("NO_BUDGET"),
                eq("No monthly budget is set for this period."), eq(false));
    }
}
