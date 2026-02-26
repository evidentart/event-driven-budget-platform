package com.sea.budgetservice.repository;

import com.sea.budgetservice.model.CategorySpending;
import com.sea.budgetservice.model.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategorySpendingRepository extends JpaRepository<CategorySpending, UUID> {

    List<CategorySpending> findAllByBudgetId(UUID budgetId);

    Optional<CategorySpending> findByBudgetIdAndCategory(UUID budgetId, ExpenseCategory category);
}
