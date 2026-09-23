package com.sea.expenseservice.repository;

import com.sea.expenseservice.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

    @Repository
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    List<Expense> findByOwnerSubjectOrderByExpenseDateDesc(String ownerSubject);

    Optional<Expense> findByIdAndOwnerSubject(UUID id, String ownerSubject);
}
