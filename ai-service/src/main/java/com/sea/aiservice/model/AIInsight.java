package com.sea.aiservice.model;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * MongoDB document storing AI-generated budget insights per expense.
 */
@Document(collection = "ai_insights")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AIInsight {

    @Id
    private String id;

    private String ownerSubject;
    private UUID expenseId;
    private ExpenseCategory expenseCategory;
    private SeverityLevel severity;
    private String budgetSummaryMessage;
    private List<String> spendingImprovements;
    private List<String> savingSuggestions;
    private List<String> budgetWarnings;

    @CreatedDate
    private LocalDateTime createdAt;
}
