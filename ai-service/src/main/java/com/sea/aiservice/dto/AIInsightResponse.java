package com.sea.aiservice.dto;

import com.sea.aiservice.model.ExpenseCategory;
import com.sea.aiservice.model.SeverityLevel;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * User-facing DTO for AI-generated insights.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIInsightResponse {

    private String id;
    private UUID userId;
    private UUID expenseId;
    private ExpenseCategory category;
    private SeverityLevel severity;
    private String budgetSummary;
    private List<String> spendingImprovements;
    private List<String> savingSuggestions;
    private List<String> budgetWarnings;
    private LocalDateTime createdAt;
}
