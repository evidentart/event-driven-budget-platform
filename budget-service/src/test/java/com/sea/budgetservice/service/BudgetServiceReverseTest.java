package com.sea.budgetservice.service;

import com.sea.budgetservice.model.Budget;
import com.sea.budgetservice.model.CategorySpending;
import com.sea.budgetservice.model.ExpenseCategory;
import com.sea.budgetservice.policy.AccountingPeriodResolver;
import com.sea.budgetservice.policy.BudgetPolicyEvaluator;
import com.sea.budgetservice.repository.BudgetRepository;
import com.sea.budgetservice.repository.CategorySpendingRepository;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BudgetServiceReverseTest {

    @Mock BudgetRepository budgetRepository;
    @Mock CategorySpendingRepository categorySpendingRepository;
    @Mock AiCommandOutboxWriter aiCommandOutboxWriter;

    @Test
    void reversesExactCentsFromBudgetAndCategory() {
        Clock clock = Clock.fixed(Instant.parse("2026-02-10T12:00:00Z"), ZoneOffset.UTC);
        BudgetService service = new BudgetService(
                budgetRepository,
                categorySpendingRepository,
                aiCommandOutboxWriter,
                new BudgetPolicyEvaluator(),
                new AccountingPeriodResolver(clock, ZoneOffset.UTC)
        );

        Budget budget = Budget.builder()
                .id(UUID.randomUUID())
                .ownerSubject("alice")
                .period("2026-02")
                .monthlyBudget(new BigDecimal("100.00"))
                .usedBudget(new BigDecimal("25.00"))
                .alertSent(false)
                .build();
        CategorySpending spending = CategorySpending.builder()
                .id(UUID.randomUUID())
                .budget(budget)
                .category(ExpenseCategory.FOOD)
                .amountSpent(new BigDecimal("25.00"))
                .build();
        when(budgetRepository.findByOwnerSubjectAndPeriodForUpdate("alice", "2026-02"))
                .thenReturn(Optional.of(budget));
        when(categorySpendingRepository.findByBudgetIdAndCategory(budget.getId(), ExpenseCategory.FOOD))
                .thenReturn(Optional.of(spending));

        service.reverseExpense(
                UUID.randomUUID(),
                "alice",
                Instant.parse("2026-02-10T12:00:00Z"),
                new BigDecimal("12.34"),
                ExpenseCategory.FOOD
        );

        verify(budgetRepository).save(budget);
        verify(categorySpendingRepository).save(spending);
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("12.66"), budget.getUsedBudget());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("12.66"), spending.getAmountSpent());
        verify(aiCommandOutboxWriter).enqueueDeletion(
                any(), eq("alice"), any(), eq("2026-02"));
    }
}
