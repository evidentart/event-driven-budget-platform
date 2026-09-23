package com.sea.expenseservice.service;

import com.sea.expenseservice.dto.ExpenseRequest;
import com.sea.expenseservice.grpc.BudgetAdvisory;
import com.sea.expenseservice.grpc.BudgetPolicyClient;
import com.sea.expenseservice.model.ExpenseType;
import com.sea.expenseservice.repository.ExpenseOutboxRepository;
import com.sea.expenseservice.repository.ExpenseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class ExpenseTransactionalOutboxTest {

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private ExpenseRepository expenseRepository;

    @MockitoBean
    private BudgetPolicyClient budgetPolicyClient;

    @MockitoBean
    private ExpenseOutboxRepository expenseOutboxRepository;

    @Test
    void rollsBackExpenseWhenOutboxWriteFails() {
        expenseRepository.deleteAll();
        when(budgetPolicyClient.evaluate(any(), any(), any()))
                .thenReturn(new BudgetAdvisory(true, "ON_TRACK", "", 9_000L));
        when(expenseOutboxRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("outbox unavailable"));

        ExpenseRequest request = ExpenseRequest.builder()
                .title("Groceries")
                .amount(new BigDecimal("12.34"))
                .category(ExpenseType.FOOD)
                .expenseDate(Instant.parse("2026-02-18T18:30:00Z"))
                .build();

        assertThrows(DataIntegrityViolationException.class,
                () -> expenseService.createExpense("alice", request));

        assertTrue(expenseRepository.findAll().isEmpty(),
                "the financial mutation must not commit without its outbox record");
    }
}
