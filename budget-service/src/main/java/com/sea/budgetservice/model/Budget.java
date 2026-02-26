package com.sea.budgetservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "budgets",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "period"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyBudget;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal usedBudget;

    @Column(nullable = false, length = 7)
    private String period; // YYYY-MM

    @Column(nullable = false)
    private Boolean alertSent;

    /**
     * Allows deleting Budget to cascade-delete CategorySpending rows first.
     */
    @OneToMany(mappedBy = "budget", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CategorySpending> categorySpendings = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @UpdateTimestamp
    @Column(name = "updated_date")
    private LocalDateTime updatedDate;

    public BigDecimal getRemainingBudget() {
        if (monthlyBudget == null) return BigDecimal.ZERO;
        return monthlyBudget.subtract(usedBudget == null ? BigDecimal.ZERO : usedBudget);
    }

    public double getPercentageUsed() {
        if (monthlyBudget == null || monthlyBudget.compareTo(BigDecimal.ZERO) <= 0) return 0.0;
        BigDecimal used = usedBudget == null ? BigDecimal.ZERO : usedBudget;
        return used
                .divide(monthlyBudget, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    public boolean isBudgetExceeded() {
        if (monthlyBudget == null) return false;
        BigDecimal used = usedBudget == null ? BigDecimal.ZERO : usedBudget;
        return used.compareTo(monthlyBudget) > 0;
    }

    public static String getCurrentPeriod() {
        return YearMonth.now().toString(); // "YYYY-MM"
    }
}

