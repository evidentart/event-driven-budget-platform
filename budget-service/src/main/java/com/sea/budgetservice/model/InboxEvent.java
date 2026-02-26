package com.sea.budgetservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Inbox entity used to ensure idempotent processing of expense events.
 */
@Entity
@Table(
        name = "inbox_events",
        uniqueConstraints = @UniqueConstraint(columnNames = "event_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(nullable = false)
    private LocalDateTime receivedAt;
}
