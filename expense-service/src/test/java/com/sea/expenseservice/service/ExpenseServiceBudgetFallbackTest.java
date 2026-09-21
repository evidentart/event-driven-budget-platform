package com.sea.expenseservice.service;

import com.sea.expenseservice.dto.ExpenseRequest;
import com.sea.expenseservice.dto.ExpenseResponse;
import com.sea.expenseservice.grpc.BudgetAdvisory;
import com.sea.expenseservice.grpc.BudgetPolicyClient;
import com.sea.expenseservice.mapper.ExpenseMapper;
import com.sea.expenseservice.model.Expense;
import com.sea.expenseservice.model.ExpenseType;
import com.sea.expenseservice.repository.ExpenseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceBudgetFallbackTest {

    @Mock ExpenseRepository expenseRepository;
    @Mock ExpenseMapper expenseMapper;
    @Mock BudgetPolicyClient budgetPolicyClient;
    @Mock ExpenseOutboxWriter expenseOutboxWriter;
    @InjectMocks ExpenseService expenseService;

    @Test
    void createsExpenseWhenBudgetAdvisoryIsUnavailable() {
        Instant expenseDate = Instant.parse("2026-02-10T12:00:00Z");
        ExpenseRequest request = ExpenseRequest.builder()
                .title("Groceries")
                .amount(new BigDecimal("12.34"))
                .category(ExpenseType.FOOD)
                .expenseDate(expenseDate)
                .build();
        Expense expense = new Expense();
        ExpenseResponse base = ExpenseResponse.builder()
                .amount("12.34")
                .expenseDate(expenseDate)
                .category(ExpenseType.FOOD)
                .build();

        when(budgetPolicyClient.evaluate("alice", expenseDate, new BigDecimal("12.34")))
                .thenReturn(BudgetAdvisory.unavailable("Budget service unavailable."));
        when(expenseMapper.toEntity("alice", request)).thenReturn(expense);
        when(expenseRepository.saveAndFlush(expense)).thenReturn(expense);
        when(expenseMapper.toResponse(expense)).thenReturn(base);

        ExpenseResponse response = expenseService.createExpense("alice", request);

        assertEquals("UNAVAILABLE", response.getBudgetStatus());
        assertEquals("Budget service unavailable.", response.getBudgetWarning());
        verify(expenseOutboxWriter).enqueueCreated(expense);
    }
}
