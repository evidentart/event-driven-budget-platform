package com.sea.expenseservice.dto;

import com.sea.expenseservice.model.ExpenseType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ExpenseResponse {
    private UUID id;
    private String title;
    private String description;
    private BigDecimal amount;
    private ExpenseType category;
    private LocalDateTime expenseDate;
    private String budgetStatus;
    private String budgetWarning;
    private Long remainingBudgetCentsAfter;
}
