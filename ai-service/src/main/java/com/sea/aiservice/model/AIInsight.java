package com.sea.aiservice.model;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
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
    private Integer generation;
    private String logicalKey;
    private InsightLifecycleStatus lifecycleStatus;
    private UUID commandId;
    private UUID sourceExpenseEventId;
    private ExpenseCategory expenseCategory;
    private SeverityLevel severity;
    private String budgetSummaryMessage;
    private List<String> spendingImprovements;
    private List<String> savingSuggestions;
    private List<String> budgetWarnings;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    private Instant deletedAt;
}
