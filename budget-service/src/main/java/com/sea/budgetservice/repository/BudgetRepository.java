package com.sea.budgetservice.repository;

import com.sea.budgetservice.model.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    Optional<Budget> findByUserIdAndPeriod(UUID userId, String period);

    List<Budget> findAllByUserIdOrderByPeriodDesc(UUID userId);
}
