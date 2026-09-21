package com.sea.budgetservice.repository;

import com.sea.budgetservice.model.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    Optional<Budget> findByOwnerSubjectAndPeriod(String ownerSubject, String period);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Budget b where b.ownerSubject = :ownerSubject and b.period = :period")
    Optional<Budget> findByOwnerSubjectAndPeriodForUpdate(String ownerSubject, String period);

    List<Budget> findAllByOwnerSubjectOrderByPeriodDesc(String ownerSubject);

    Optional<Budget> findByIdAndOwnerSubject(UUID id, String ownerSubject);
}
