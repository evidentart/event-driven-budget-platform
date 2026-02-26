package com.sea.budgetservice.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateBudgetRequest {

    @NotNull(message = "Monthly budget amount is required")
    @DecimalMin(value = "0.01", message = "Budget must be greater than zero")
    @Digits(integer = 10, fraction = 2, message = "Budget must be a valid amount")
    private BigDecimal monthlyBudget;
}
