package com.sea.budgetservice.repository;

import com.sea.budgetservice.model.AiCommandOutbox;
import com.sea.budgetservice.model.AiCommandOutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AiCommandOutboxRepository extends JpaRepository<AiCommandOutbox, Long> {

    @Query("""
            select o from AiCommandOutbox o
            where o.status in :statuses
              and (
                    (o.status = :inFlightStatus
                        and (o.lockedUntil is null or o.lockedUntil <= :now))
                    or
                    (o.status <> :inFlightStatus
                        and (o.nextAttemptAt is null or o.nextAttemptAt <= :now))
              )
            order by o.outboxSequence asc
            """)
    List<AiCommandOutbox> findFirstEligibleOrderByOutboxSequenceAsc(
            @Param("statuses") Collection<AiCommandOutboxStatus> statuses,
            @Param("inFlightStatus") AiCommandOutboxStatus inFlightStatus,
            @Param("now") java.time.Instant now,
            Pageable pageable
    );
}
