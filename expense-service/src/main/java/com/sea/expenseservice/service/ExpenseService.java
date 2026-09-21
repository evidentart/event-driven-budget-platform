package com.sea.expenseservice.service;

import com.sea.expenseservice.dto.ExpenseRequest;
import com.sea.expenseservice.dto.ExpenseResponse;
import com.sea.expenseservice.exception.ExpenseNotFoundException;
import com.sea.expenseservice.grpc.BudgetAdvisory;
import com.sea.expenseservice.grpc.BudgetPolicyClient;
import com.sea.expenseservice.mapper.ExpenseMapper;
import com.sea.expenseservice.model.Expense;
import com.sea.expenseservice.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseMapper expenseMapper;
    private final BudgetPolicyClient budgetPolicyClient;
    private final ExpenseOutboxWriter expenseOutboxWriter;

    @Transactional
    public ExpenseResponse createExpense(String ownerSubject, ExpenseRequest request) {

        BudgetAdvisory advisory = budgetPolicyClient.evaluate(
                ownerSubject,
                request.getExpenseDate(),
                request.getAmount()
        );

        if (!advisory.available()) {
            log.warn("Budget gRPC advisory unavailable; expense creation will continue");
        } else if (!advisory.warning().isBlank()) {
            log.warn("Budget advisory status={} warning={}", advisory.status(), advisory.warning());
        }

        Expense expense = expenseMapper.toEntity(ownerSubject, request);
        Expense saved = expenseRepository.saveAndFlush(expense);

        log.info("Expense created id={} ownerSubject={} amount={} category={}",
                saved.getId(), saved.getOwnerSubject(), saved.getAmount(), saved.getCategory());

        expenseOutboxWriter.enqueueCreated(saved);

        ExpenseResponse base = expenseMapper.toResponse(saved);

        // ---- Option B response enrichment ----
        String budgetStatus;
        String budgetWarning;
        Long remaining;

        budgetStatus = advisory.status();
        budgetWarning = advisory.warning();
        remaining = advisory.remainingCentsAfter();

        return ExpenseResponse.builder()
                .id(base.getId())
                .title(base.getTitle())
                .description(base.getDescription())
                .amount(base.getAmount())
                .category(base.getCategory())
                .expenseDate(base.getExpenseDate())
                .budgetStatus(budgetStatus)
                .budgetWarning(budgetWarning)
                .remainingBudgetAfter(remaining == null ? null : BigDecimal.valueOf(remaining, 2).toPlainString())
                .build();
    }

    @Transactional(readOnly = true)
    public ExpenseResponse getExpenseById(String ownerSubject, UUID expenseId) {
        Expense expense = expenseRepository.findByIdAndOwnerSubject(expenseId, ownerSubject)
                .orElseThrow(() -> new ExpenseNotFoundException(expenseId));

        // For non-create endpoints, budget decision isn't calculated. Return without warning fields.
        return ExpenseResponse.builder()
                .id(expense.getId())
                .title(expense.getTitle())
                .description(expense.getDescription())
                .amount(expense.getAmount() == null ? null : expense.getAmount().toPlainString())
                .category(expense.getCategory())
                .expenseDate(expense.getExpenseDate())
                .build();
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> listExpensesByOwner(String ownerSubject) {
        return expenseRepository.findByOwnerSubjectOrderByExpenseDateDesc(ownerSubject)
                .stream()
                .map(e -> ExpenseResponse.builder()
                        .id(e.getId())
                        .title(e.getTitle())
                        .description(e.getDescription())
                        .amount(e.getAmount() == null ? null : e.getAmount().toPlainString())
                        .category(e.getCategory())
                        .expenseDate(e.getExpenseDate())
                        .build())
                .toList();
    }

    @Transactional
    public void deleteExpense(String ownerSubject, UUID expenseId) {
        Expense expense = expenseRepository.findByIdAndOwnerSubject(expenseId, ownerSubject)
                .orElseThrow(() -> new ExpenseNotFoundException(expenseId));

        expenseOutboxWriter.enqueueDeleted(expense);
        expenseRepository.delete(expense);
        log.info("Deleted expense id={}", expenseId);
    }
}
