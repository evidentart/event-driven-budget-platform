package com.sea.aiservice.repository;

import com.sea.aiservice.model.AIInsight;
import com.sea.aiservice.model.ExpenseCategory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AIInsightRepository extends MongoRepository<AIInsight, String> {

    List<AIInsight> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<AIInsight> findByUserIdAndExpenseCategory(UUID userId, ExpenseCategory expenseCategory);

    Optional<AIInsight> findTopByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<AIInsight> findByExpenseId(UUID expenseId);

    Optional<AIInsight> findByUserIdAndExpenseId(UUID userId, UUID expenseId);

    boolean existsByExpenseId(UUID expenseId);

    long deleteByUserId(UUID userId);
}

