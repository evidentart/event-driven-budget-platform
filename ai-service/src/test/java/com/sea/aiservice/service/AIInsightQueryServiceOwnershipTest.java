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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class AIInsightQueryServiceOwnershipTest {

    @Mock
    private AIInsightRepository repository;

    @Mock
    private AIInsightService insightService;

    @InjectMocks
    private AIInsightQueryService queryService;

    @Test
    void rejectsAnInsightOwnedByAnotherSubject() {
        UUID expenseId = UUID.randomUUID();
        when(repository.findByOwnerSubjectAndExpenseIdAndGenerationAndLifecycleStatus(
                eq("bob"), eq(expenseId), eq(1), any()))
                .thenReturn(java.util.Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> queryService.getInsightByExpense("bob", expenseId));
    }

    @Test
    void deletesOnlyAnInsightOwnedByTheAuthenticatedSubject() {
        UUID expenseId = UUID.randomUUID();
        queryService.deleteInsightByOwnerAndExpense("alice", expenseId);

        verify(insightService).deleteForOwner("alice", expenseId);
    }
}
