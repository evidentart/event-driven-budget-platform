package com.sea.aiservice.controller;

import com.sea.aiservice.dto.AIInsightResponse;
import com.sea.aiservice.model.ExpenseCategory;
import com.sea.aiservice.service.AIInsightQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
@Slf4j
public class AIInsightController {

    private final AIInsightQueryService aiInsightQueryService;

    @GetMapping("/{userId}")
    public ResponseEntity<List<AIInsightResponse>> getInsightsByUser(@PathVariable UUID userId) {
        log.info("Fetching all insights for user: {}", userId);
        return ResponseEntity.ok(aiInsightQueryService.getInsightsByUser(userId));
    }

    @GetMapping("/{userId}/category/{category}")
    public ResponseEntity<List<AIInsightResponse>> getInsightsByCategory(
            @PathVariable UUID userId,
            @PathVariable ExpenseCategory category
    ) {
        log.info("Fetching insights for user {} in category {}", userId, category);
        return ResponseEntity.ok(aiInsightQueryService.getInsightsByUserAndCategory(userId, category));
    }

    @GetMapping("/{userId}/latest")
    public ResponseEntity<AIInsightResponse> getLatestInsight(@PathVariable UUID userId) {
        log.info("Fetching latest insight for user: {}", userId);
        return ResponseEntity.ok(aiInsightQueryService.getLatestInsight(userId));
    }

    @GetMapping("/expense/{expenseId}")
    public ResponseEntity<AIInsightResponse> getInsightByExpense(@PathVariable UUID expenseId) {
        log.info("Fetching insight for expense: {}", expenseId);
        return ResponseEntity.ok(aiInsightQueryService.getInsightByExpense(expenseId));
    }

    // Delete a single insight for a specific expense.
    @DeleteMapping("/{userId}/expense/{expenseId}")
    public ResponseEntity<Void> deleteInsightByExpense(
            @PathVariable UUID userId,
            @PathVariable UUID expenseId
    ) {
        log.info("Deleting insight for userId={} expenseId={}", userId, expenseId);
        aiInsightQueryService.deleteInsightByUserAndExpense(userId, expenseId);
        return ResponseEntity.noContent().build();
    }

    // Delete all insights for a user.
    @DeleteMapping("/{userId}")
    public ResponseEntity<Long> deleteAllInsightsForUser(@PathVariable UUID userId) {
        log.info("Deleting ALL insights for userId={}", userId);
        long deleted = aiInsightQueryService.deleteAllInsightsForUser(userId);
        return ResponseEntity.ok(deleted);
    }
}

