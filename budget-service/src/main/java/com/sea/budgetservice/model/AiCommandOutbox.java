package com.sea.budgetservice.model;

import com.sea.budgetservice.ai.AiCommandType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "ai_command_outbox",
        indexes = @Index(name = "idx_ai_command_outbox_status_sequence", columnList = "status,outbox_sequence"),
        uniqueConstraints = @UniqueConstraint(name = "uk_ai_command_outbox_command_id", columnNames = "command_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiCommandOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ai-command-outbox-sequence")
    @SequenceGenerator(
            name = "ai-command-outbox-sequence",
            sequenceName = "ai_command_outbox_sequence",
            allocationSize = 1
    )
    @Column(name = "outbox_sequence")
    private Long outboxSequence;

    @Column(name = "command_id", nullable = false, unique = true)
    private UUID commandId;

    @Enumerated(EnumType.STRING)
    @Column(name = "command_type", nullable = false, length = 50)
    private AiCommandType commandType;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(name = "owner_subject", nullable = false, length = 255)
    private String ownerSubject;

    @Column(name = "expense_id", nullable = false)
    private UUID expenseId;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private AiCommandOutboxStatus status = AiCommandOutboxStatus.PENDING;

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
