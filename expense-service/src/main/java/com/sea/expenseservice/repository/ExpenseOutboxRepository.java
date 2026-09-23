package com.sea.expenseservice.repository;

import com.sea.expenseservice.model.ExpenseOutboxEvent;
import com.sea.expenseservice.model.ExpenseOutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface ExpenseOutboxRepository extends JpaRepository<ExpenseOutboxEvent, Long> {

    Optional<ExpenseOutboxEvent> findFirstByStatusInOrderByOutboxSequenceAsc(
            Collection<ExpenseOutboxStatus> statuses
    );
}
