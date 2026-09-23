package com.sea.budgetservice.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryDetail {
    private String amount;
    private java.math.BigDecimal percentage;
}
