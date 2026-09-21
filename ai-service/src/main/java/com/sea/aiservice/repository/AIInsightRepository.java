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

    List<AIInsight> findByOwnerSubjectOrderByCreatedAtDesc(String ownerSubject);

    List<AIInsight> findByOwnerSubjectAndExpenseCategory(String ownerSubject, ExpenseCategory expenseCategory);

    Optional<AIInsight> findTopByOwnerSubjectOrderByCreatedAtDesc(String ownerSubject);

    Optional<AIInsight> findByExpenseId(UUID expenseId);

    Optional<AIInsight> findByOwnerSubjectAndExpenseId(String ownerSubject, UUID expenseId);

    boolean existsByExpenseId(UUID expenseId);

    long deleteByOwnerSubject(String ownerSubject);
}

