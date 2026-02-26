package com.sea.budgetservice.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Monthly budget is required")
    @DecimalMin(value = "0.01", message = "Budget must be greater than zero")
    @Digits(integer = 10, fraction = 2, message = "Budget must be a valid amount")
    private BigDecimal monthlyBudget;

    @Pattern(
            regexp = "^\\d{4}-(0[1-9]|1[0-2])$",
            message = "Period must be in format YYYY-MM"
    )
    private String period; // optional, defaults to current period
}
