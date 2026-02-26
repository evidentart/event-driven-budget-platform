package com.sea.budgetservice.repository;

import com.sea.budgetservice.model.InboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface InboxEventRepository extends JpaRepository<InboxEvent, UUID> {
    boolean existsByEventId(UUID eventId);
}
