package com.sea.aiservice.repository;

import com.sea.aiservice.model.AIInsight;
import com.sea.aiservice.model.ExpenseCategory;
import com.sea.aiservice.model.InsightLifecycleStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AIInsightRepository extends MongoRepository<AIInsight, String> {

    List<AIInsight> findByOwnerSubjectAndLifecycleStatusOrderByCreatedAtDesc(
            String ownerSubject, InsightLifecycleStatus lifecycleStatus);

    List<AIInsight> findByOwnerSubjectAndExpenseCategoryAndLifecycleStatus(
            String ownerSubject, ExpenseCategory expenseCategory, InsightLifecycleStatus lifecycleStatus);

    Optional<AIInsight> findTopByOwnerSubjectAndLifecycleStatusOrderByCreatedAtDesc(
            String ownerSubject, InsightLifecycleStatus lifecycleStatus);

    Optional<AIInsight> findByOwnerSubjectAndExpenseIdAndGeneration(
            String ownerSubject, UUID expenseId, Integer generation);

    Optional<AIInsight> findByOwnerSubjectAndExpenseIdAndGenerationAndLifecycleStatus(
            String ownerSubject, UUID expenseId, Integer generation, InsightLifecycleStatus lifecycleStatus);

    List<AIInsight> findByOwnerSubject(String ownerSubject);

}

