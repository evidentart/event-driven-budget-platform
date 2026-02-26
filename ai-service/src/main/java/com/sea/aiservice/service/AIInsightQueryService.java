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

    public List<AIInsightResponse> getInsightsByUser(UUID userId) {
        List<AIInsight> insights = aiInsightRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return insights.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<AIInsightResponse> getInsightsByUserAndCategory(UUID userId, ExpenseCategory category) {
        List<AIInsight> insights = aiInsightRepository.findByUserIdAndExpenseCategory(userId, category);
        return insights.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public AIInsightResponse getLatestInsight(UUID userId) {
        AIInsight insight = aiInsightRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No insights found for user: " + userId));
        return toResponse(insight);
    }

    public AIInsightResponse getInsightByExpense(UUID expenseId) {
        AIInsight insight = aiInsightRepository.findByExpenseId(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("No insight found for expense: " + expenseId));
        return toResponse(insight);
    }

    // Delete one insight for a specific expense.
    public void deleteInsightByUserAndExpense(UUID userId, UUID expenseId) {
        AIInsight insight = aiInsightRepository.findByUserIdAndExpenseId(userId, expenseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No insight found for userId=" + userId + " and expenseId=" + expenseId
                ));

        aiInsightRepository.delete(insight);
        log.info("Deleted insight id={} for userId={} expenseId={}", insight.getId(), userId, expenseId);
    }

    // Delete all insights for a user.
    public long deleteAllInsightsForUser(UUID userId) {
        long deleted = aiInsightRepository.deleteByUserId(userId);
        log.info("Deleted {} insights for userId={}", deleted, userId);
        return deleted;
    }

    private AIInsightResponse toResponse(AIInsight insight) {
        return AIInsightResponse.builder()
                .id(insight.getId())
                .userId(insight.getUserId())
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

