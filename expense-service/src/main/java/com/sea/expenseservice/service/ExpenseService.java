package com.sea.expenseservice.service;

import com.sea.budget.policy.v1.CanSpendResponse;
import com.sea.expenseservice.dto.ExpenseRequest;
import com.sea.expenseservice.dto.ExpenseResponse;
import com.sea.expenseservice.exception.ExpenseNotFoundException;
import com.sea.expenseservice.grpc.BudgetPolicyClient;
import com.sea.expenseservice.kafka.ExpenseEventProducer;
import com.sea.expenseservice.mapper.ExpenseMapper;
import com.sea.expenseservice.model.Expense;
import com.sea.expenseservice.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseMapper expenseMapper;
    private final BudgetPolicyClient budgetPolicyClient;
    private final ExpenseEventProducer expenseEventProducer;

    @Transactional
    public ExpenseResponse createExpense(ExpenseRequest request) {

        String period = YearMonth.from(request.getExpenseDate()).toString();
        String category = request.getCategory().name();

        CanSpendResponse decision = null;

        try {
            decision = budgetPolicyClient.canSpend(
                    request.getUserId().toString(),
                    period,
                    category,
                    request.getAmount()
            );

            if (!decision.getWarning().isBlank()) {
                log.warn("Budget warning userId={} period={} status={} warning={}",
                        request.getUserId(), period, decision.getStatus(), decision.getWarning());
            }
        } catch (Exception ex) {
            // Option B: warn-only, continue creating the expense.
            log.warn("Budget gRPC check failed (warn-only mode continues): {}", ex.getMessage(), ex);
        }

        Expense expense = expenseMapper.toEntity(request);
        Expense saved = expenseRepository.saveAndFlush(expense);

        log.info("Expense created id={} userId={} amount={} category={}",
                saved.getId(), saved.getUserId(), saved.getAmount(), saved.getCategory());

        expenseEventProducer.sendExpenseCreated(saved);


        ExpenseResponse base = expenseMapper.toResponse(saved);

        // ---- Option B response enrichment ----
        String budgetStatus;
        String budgetWarning;
        Long remaining;

        if (decision == null) {
            budgetStatus = "UNAVAILABLE";
            budgetWarning = "Budget check unavailable right now.";
            remaining = null;
        } else {
            budgetStatus = decision.getStatus().isBlank() ? "UNKNOWN" : decision.getStatus();
            budgetWarning = decision.getWarning(); // can be empty
            remaining = decision.getRemainingCentsAfter();
        }

        return ExpenseResponse.builder()
                .id(base.getId())
                .title(base.getTitle())
                .description(base.getDescription())
                .amount(base.getAmount())
                .category(base.getCategory())
                .expenseDate(base.getExpenseDate())
                .budgetStatus(budgetStatus)
                .budgetWarning(budgetWarning)
                .remainingBudgetCentsAfter(remaining)
                .build();
    }

    @Transactional(readOnly = true)
    public ExpenseResponse getExpenseById(UUID expenseId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ExpenseNotFoundException(expenseId));

        // For non-create endpoints, budget decision isn't calculated. Return without warning fields.
        return ExpenseResponse.builder()
                .id(expense.getId())
                .title(expense.getTitle())
                .description(expense.getDescription())
                .amount(expense.getAmount())
                .category(expense.getCategory())
                .expenseDate(expense.getExpenseDate())
                .build();
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> listExpensesByUser(UUID userId) {
        return expenseRepository.findByUserId(userId)
                .stream()
                .map(e -> ExpenseResponse.builder()
                        .id(e.getId())
                        .title(e.getTitle())
                        .description(e.getDescription())
                        .amount(e.getAmount())
                        .category(e.getCategory())
                        .expenseDate(e.getExpenseDate())
                        .build())
                .toList();
    }

    @Transactional
    public void deleteExpense(UUID expenseId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ExpenseNotFoundException(expenseId));

        expenseRepository.delete(expense);
        log.info("Deleted expense id={}", expenseId);
    }
}
