package com.sea.expenseservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "expenses",
        indexes = {
                @Index(name = "idx_expenses_owner_subject", columnList = "owner_subject"),
                @Index(name = "idx_expenses_expense_date", columnList = "expense_date")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_subject", nullable = false, length = 255)
    private String ownerSubject;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 255)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExpenseType category;

    @Column(name = "expense_date", nullable = false)
    private Instant expenseDate;

    @CreationTimestamp
    @Column(name = "created_date", updatable = false)
    private Instant createdDate;

    @UpdateTimestamp
    @Column(name = "updated_date")
    private Instant updatedDate;
}
