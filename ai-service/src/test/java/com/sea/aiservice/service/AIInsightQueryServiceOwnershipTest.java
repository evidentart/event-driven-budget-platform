package com.sea.aiservice.service;

import com.sea.aiservice.exception.ResourceNotFoundException;
import com.sea.aiservice.repository.AIInsightRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AIInsightQueryServiceOwnershipTest {

    @Mock
    private AIInsightRepository repository;

    @InjectMocks
    private AIInsightQueryService queryService;

    @Test
    void rejectsAnInsightOwnedByAnotherSubject() {
        UUID expenseId = UUID.randomUUID();
        when(repository.findByOwnerSubjectAndExpenseId("bob", expenseId))
                .thenReturn(java.util.Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> queryService.getInsightByExpense("bob", expenseId));
    }

    @Test
    void deletesOnlyAnInsightOwnedByTheAuthenticatedSubject() {
        UUID expenseId = UUID.randomUUID();
        when(repository.findByOwnerSubjectAndExpenseId("alice", expenseId))
                .thenReturn(java.util.Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> queryService.deleteInsightByOwnerAndExpense("alice", expenseId));

        verify(repository).findByOwnerSubjectAndExpenseId("alice", expenseId);
    }
}
