package com.sea.budgetservice.repository;

import com.sea.budgetservice.model.InboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface InboxEventRepository extends JpaRepository<InboxEvent, UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO inbox_events (id, event_id, received_at, event_type, schema_version)
            VALUES (:id, :eventId, :receivedAt, :eventType, :schemaVersion)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("eventId") UUID eventId,
            @Param("eventType") String eventType,
            @Param("schemaVersion") int schemaVersion,
            @Param("receivedAt") Instant receivedAt
    );
}
