package com.sea.budgetservice.dto;

import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryDetail {
    private BigDecimal amount;
    private double percentage;
}
