package com.sea.expenseservice.service;

import com.sea.expenseservice.exception.ExpenseNotFoundException;
import com.sea.expenseservice.grpc.BudgetPolicyClient;
import com.sea.expenseservice.mapper.ExpenseMapper;
import com.sea.expenseservice.model.Expense;
import com.sea.expenseservice.repository.ExpenseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceOwnershipTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private ExpenseMapper expenseMapper;

    @Mock
    private BudgetPolicyClient budgetPolicyClient;

    @Mock
    private ExpenseOutboxWriter expenseOutboxWriter;

    @InjectMocks
    private ExpenseService expenseService;

    @Test
    void readsOnlyAnExpenseOwnedByTheAuthenticatedSubject() {
        UUID expenseId = UUID.randomUUID();
        Expense expense = new Expense();
        expense.setId(expenseId);
        expense.setOwnerSubject("alice");

        when(expenseRepository.findByIdAndOwnerSubject(expenseId, "alice"))
                .thenReturn(Optional.of(expense));

        expenseService.getExpenseById("alice", expenseId);

        verify(expenseRepository).findByIdAndOwnerSubject(expenseId, "alice");
    }

    @Test
    void rejectsAnExpenseOwnedByAnotherSubject() {
        UUID expenseId = UUID.randomUUID();
        when(expenseRepository.findByIdAndOwnerSubject(expenseId, "bob"))
                .thenReturn(Optional.empty());

        assertThrows(ExpenseNotFoundException.class,
                () -> expenseService.getExpenseById("bob", expenseId));
    }
}
