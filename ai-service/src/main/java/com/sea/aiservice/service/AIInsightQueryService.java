package com.sea.aiservice.service;

import com.sea.aiservice.dto.AIInsightResponse;
import com.sea.aiservice.exception.ResourceNotFoundException;
import com.sea.aiservice.model.AIInsight;
import com.sea.aiservice.model.ExpenseCategory;
import com.sea.aiservice.repository.AIInsightRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AIInsightQueryService {

    private final AIInsightRepository aiInsightRepository;

    public List<AIInsightResponse> getInsightsByOwner(String ownerSubject) {
        List<AIInsight> insights = aiInsightRepository.findByOwnerSubjectOrderByCreatedAtDesc(ownerSubject);
        return insights.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<AIInsightResponse> getInsightsByOwnerAndCategory(String ownerSubject, ExpenseCategory category) {
        List<AIInsight> insights = aiInsightRepository.findByOwnerSubjectAndExpenseCategory(ownerSubject, category);
        return insights.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public AIInsightResponse getLatestInsight(String ownerSubject) {
        AIInsight insight = aiInsightRepository.findTopByOwnerSubjectOrderByCreatedAtDesc(ownerSubject)
                .orElseThrow(() -> new ResourceNotFoundException("No insights found for owner"));
        return toResponse(insight);
    }

    public AIInsightResponse getInsightByExpense(String ownerSubject, UUID expenseId) {
        AIInsight insight = aiInsightRepository.findByOwnerSubjectAndExpenseId(ownerSubject, expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("No insight found for expense: " + expenseId));
        return toResponse(insight);
    }

    // Delete one insight for a specific expense.
    public void deleteInsightByOwnerAndExpense(String ownerSubject, UUID expenseId) {
        AIInsight insight = aiInsightRepository.findByOwnerSubjectAndExpenseId(ownerSubject, expenseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No insight found for owner and expenseId=" + expenseId
                ));

        aiInsightRepository.delete(insight);
        log.info("Deleted insight id={} for ownerSubject={} expenseId={}", insight.getId(), ownerSubject, expenseId);
    }

    // Delete all insights for a user.
    public long deleteAllInsightsForOwner(String ownerSubject) {
        long deleted = aiInsightRepository.deleteByOwnerSubject(ownerSubject);
        log.info("Deleted {} insights for ownerSubject={}", deleted, ownerSubject);
        return deleted;
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

