package com.sea.aiservice.service;

import com.sea.aiservice.dto.AIInsightResponse;
import com.sea.aiservice.exception.ResourceNotFoundException;
import com.sea.aiservice.model.AIInsight;
import com.sea.aiservice.model.ExpenseCategory;
import com.sea.aiservice.model.InsightLifecycleStatus;
import com.sea.aiservice.repository.AIInsightRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AIInsightQueryService {

    private final AIInsightRepository repository;
    private final AIInsightService insightService;

    public List<AIInsightResponse> getInsightsByOwner(String ownerSubject) {
        return repository.findByOwnerSubjectAndLifecycleStatusOrderByCreatedAtDesc(
                        ownerSubject, InsightLifecycleStatus.ACTIVE)
                .stream().map(this::toResponse).toList();
    }

    public List<AIInsightResponse> getInsightsByOwnerAndCategory(String ownerSubject, ExpenseCategory category) {
        return repository.findByOwnerSubjectAndExpenseCategoryAndLifecycleStatus(
                        ownerSubject, category, InsightLifecycleStatus.ACTIVE)
                .stream().map(this::toResponse).toList();
    }

    public AIInsightResponse getLatestInsight(String ownerSubject) {
        return toResponse(repository.findTopByOwnerSubjectAndLifecycleStatusOrderByCreatedAtDesc(
                        ownerSubject, InsightLifecycleStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No insights found for owner")));
    }

    public AIInsightResponse getInsightByExpense(String ownerSubject, UUID expenseId) {
        return toResponse(repository.findByOwnerSubjectAndExpenseIdAndGenerationAndLifecycleStatus(
                        ownerSubject, expenseId, 1, InsightLifecycleStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No insight found for expense: " + expenseId)));
    }

    public void deleteInsightByOwnerAndExpense(String ownerSubject, UUID expenseId) {
        insightService.deleteForOwner(ownerSubject, expenseId);
    }

    public long deleteAllInsightsForOwner(String ownerSubject) {
        long count = repository.findByOwnerSubject(ownerSubject).stream()
                .filter(insight -> insight.getLifecycleStatus() == InsightLifecycleStatus.ACTIVE)
                .peek(insight -> insightService.deleteForOwner(ownerSubject, insight.getExpenseId()))
                .count();
        log.info("Deleted {} active insights for ownerSubject={}", count, ownerSubject);
        return count;
    }

    private AIInsightResponse toResponse(AIInsight insight) {
        return AIInsightResponse.builder()
                .id(insight.getId())
                .expenseId(insight.getExpenseId())
                .category(insight.getExpenseCategory())
                .severity(insight.getSeverity())
                .budgetSummary(insight.getBudgetSummaryMessage())
                .spendingImprovements(insight.getSpendingImprovements())
                .savingSuggestions(insight.getSavingSuggestions())
                .budgetWarnings(insight.getBudgetWarnings())
                .createdAt(insight.getCreatedAt())
                .build();
    }
}
