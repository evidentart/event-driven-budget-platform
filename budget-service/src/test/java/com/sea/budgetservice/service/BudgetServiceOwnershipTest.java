package com.sea.budgetservice.service;

import com.sea.budgetservice.model.Budget;
import com.sea.budgetservice.repository.BudgetRepository;
import com.sea.budgetservice.repository.CategorySpendingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.sea.budgetservice.exception.ResourceNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetServiceOwnershipTest {

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private CategorySpendingRepository categorySpendingRepository;

    @Mock
    private AiCommandOutboxWriter aiCommandOutboxWriter;

    @InjectMocks
    private BudgetService budgetService;

    @Test
    void rejectsDeletingABudgetOwnedByAnotherSubject() {
        UUID budgetId = UUID.randomUUID();
        when(budgetRepository.findByIdAndOwnerSubject(budgetId, "bob"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> budgetService.deleteBudget("bob", budgetId));
    }

    @Test
    void deletesOnlyABudgetOwnedByTheAuthenticatedSubject() {
        UUID budgetId = UUID.randomUUID();
        Budget budget = new Budget();
        budget.setId(budgetId);
        budget.setOwnerSubject("alice");
        when(budgetRepository.findByIdAndOwnerSubject(budgetId, "alice"))
                .thenReturn(Optional.of(budget));

        budgetService.deleteBudget("alice", budgetId);

        verify(budgetRepository).delete(budget);
    }
}
