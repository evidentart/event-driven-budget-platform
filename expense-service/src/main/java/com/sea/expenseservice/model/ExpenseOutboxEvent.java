package com.sea.expenseservice.model;

import com.sea.expenseservice.kafka.ExpenseEventType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "expense_outbox",
        indexes = @Index(name = "idx_expense_outbox_status_sequence", columnList = "status,outbox_sequence"),
        uniqueConstraints = @UniqueConstraint(name = "uk_expense_outbox_event_id", columnNames = "event_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "expense-outbox-sequence")
    @SequenceGenerator(
            name = "expense-outbox-sequence",
            sequenceName = "expense_outbox_sequence",
            allocationSize = 1
    )
    @Column(name = "outbox_sequence")
    private Long outboxSequence;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private ExpenseEventType eventType;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "owner_subject", nullable = false, length = 255)
    private String ownerSubject;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ExpenseOutboxStatus status = ExpenseOutboxStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;
}
