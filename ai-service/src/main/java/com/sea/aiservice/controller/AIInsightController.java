package com.sea.aiservice.controller;

import com.sea.aiservice.dto.AIInsightResponse;
import com.sea.aiservice.model.ExpenseCategory;
import com.sea.aiservice.service.AIInsightQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
@Slf4j
public class AIInsightController {

    private final AIInsightQueryService aiInsightQueryService;

    @GetMapping("/me")
    public ResponseEntity<List<AIInsightResponse>> getMyInsights(JwtAuthenticationToken auth) {
        String ownerSubject = auth.getToken().getSubject();
        log.info("Fetching all insights for ownerSubject={}", ownerSubject);
        return ResponseEntity.ok(aiInsightQueryService.getInsightsByOwner(ownerSubject));
    }

    @GetMapping("/me/category/{category}")
    public ResponseEntity<List<AIInsightResponse>> getInsightsByCategory(
            JwtAuthenticationToken auth,
            @PathVariable ExpenseCategory category
    ) {
        String ownerSubject = auth.getToken().getSubject();
        log.info("Fetching insights for ownerSubject={} in category {}", ownerSubject, category);
        return ResponseEntity.ok(aiInsightQueryService.getInsightsByOwnerAndCategory(ownerSubject, category));
    }

    @GetMapping("/me/latest")
    public ResponseEntity<AIInsightResponse> getLatestInsight(JwtAuthenticationToken auth) {
        String ownerSubject = auth.getToken().getSubject();
        log.info("Fetching latest insight for ownerSubject={}", ownerSubject);
        return ResponseEntity.ok(aiInsightQueryService.getLatestInsight(ownerSubject));
    }

    @GetMapping("/me/expense/{expenseId}")
    public ResponseEntity<AIInsightResponse> getInsightByExpense(
            JwtAuthenticationToken auth,
            @PathVariable UUID expenseId) {
        log.info("Fetching insight for expense: {}", expenseId);
        return ResponseEntity.ok(aiInsightQueryService.getInsightByExpense(
                auth.getToken().getSubject(), expenseId));
    }

    @DeleteMapping("/me/expense/{expenseId}")
    public ResponseEntity<Void> deleteInsightByExpense(
            JwtAuthenticationToken auth,
            @PathVariable UUID expenseId
    ) {
        String ownerSubject = auth.getToken().getSubject();
        log.info("Deleting insight for ownerSubject={} expenseId={}", ownerSubject, expenseId);
        aiInsightQueryService.deleteInsightByOwnerAndExpense(ownerSubject, expenseId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Long> deleteAllInsights(JwtAuthenticationToken auth) {
        String ownerSubject = auth.getToken().getSubject();
        log.info("Deleting all insights for ownerSubject={}", ownerSubject);
        long deleted = aiInsightQueryService.deleteAllInsightsForOwner(ownerSubject);
        return ResponseEntity.ok(deleted);
    }
}

