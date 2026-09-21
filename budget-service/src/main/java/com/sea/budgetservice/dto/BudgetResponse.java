package com.sea.budgetservice.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetResponse {

    private UUID id;
    private String period;
    private BigDecimal monthlyBudget;
    private BigDecimal spent;
    private BigDecimal remaining;
    private Double percentageUsed;
    private BudgetStatus status;
    private Map<String, CategoryDetail> categoryBreakdown;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
