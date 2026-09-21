package com.sea.budgetservice.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
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
    private String monthlyBudget;
    private String spent;
    private String remaining;
    private BigDecimal percentageUsed;
    private BudgetStatus status;
    private Map<String, CategoryDetail> categoryBreakdown;
    private Instant createdAt;
    private Instant updatedAt;
}
