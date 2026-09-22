package com.sea.budgetservice.service;

import com.sea.budgetservice.dto.*;
import com.sea.budgetservice.exception.DuplicateBudgetException;
import com.sea.budgetservice.exception.ResourceNotFoundException;
import com.sea.budgetservice.model.Budget;
import com.sea.budgetservice.model.CategorySpending;
import com.sea.budgetservice.model.ExpenseCategory;
import com.sea.budgetservice.policy.AccountingPeriodResolver;
import com.sea.budgetservice.policy.BudgetEvaluation;
import com.sea.budgetservice.policy.BudgetPolicyEvaluator;
import com.sea.budgetservice.repository.BudgetRepository;
import com.sea.budgetservice.repository.CategorySpendingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategorySpendingRepository categorySpendingRepository;
    private final AiCommandOutboxWriter aiCommandOutboxWriter;
    private final BudgetPolicyEvaluator policyEvaluator;
    private final AccountingPeriodResolver periodResolver;

    // -------------------------
    // REST-used methods
    // -------------------------

    @Transactional
    public BudgetResponse createBudget(String ownerSubject, BudgetRequest request) {
        validateBudgetLimit(request.getMonthlyBudget());
        String period = normalizePeriod(request.getPeriod());

        budgetRepository.findByOwnerSubjectAndPeriod(ownerSubject, period)
                .ifPresent(existing -> {
                    throw new DuplicateBudgetException("A budget already exists for this period.");
                });

        Budget budget = Budget.builder()
                .ownerSubject(ownerSubject)
                .period(period)
                .monthlyBudget(request.getMonthlyBudget())
                .usedBudget(BigDecimal.ZERO)
                .alertSent(Boolean.FALSE)
                .build();

        Budget saved = budgetRepository.save(budget);

        log.info("Saved budget ownerSubject={} period={} monthlyBudget={} used={}",
                saved.getOwnerSubject(), saved.getPeriod(), saved.getMonthlyBudget(), saved.getUsedBudget());

        return toResponse(saved, List.of());
    }

    @Transactional(readOnly = true)
    public BudgetResponse getCurrentBudget(String ownerSubject) {
        String currentPeriod = periodResolver.currentPeriod();
        Budget budget = findBudgetOr404(ownerSubject, currentPeriod);
        return buildResponseWithBreakdown(budget);
    }

    @Transactional(readOnly = true)
    public BudgetResponse getBudgetByOwnerAndPeriod(String ownerSubject, String period) {
        Budget budget = findBudgetOr404(ownerSubject, period);
        return buildResponseWithBreakdown(budget);
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> getAllBudgetsByOwner(String ownerSubject) {
        return budgetRepository.findAllByOwnerSubjectOrderByPeriodDesc(ownerSubject)
                .stream()
                .map(this::buildResponseWithBreakdown)
                .toList();
    }

    @Transactional
    public BudgetResponse setCurrentBudget(String ownerSubject, BigDecimal monthlyBudget) {
        validateBudgetLimit(monthlyBudget);
        String currentPeriod = periodResolver.currentPeriod();
        Budget budget = findBudgetOr404(ownerSubject, currentPeriod);

        budget.setMonthlyBudget(monthlyBudget);
        Budget saved = budgetRepository.save(budget);

        log.info("Updated current budget ownerSubject={} period={} monthlyBudget={} used={}",
                saved.getOwnerSubject(), saved.getPeriod(), saved.getMonthlyBudget(), saved.getUsedBudget());

        return buildResponseWithBreakdown(saved);
    }

    /**
     * Delete budget safely; CategorySpending FK will not block due to cascade.
     */
    @Transactional
    public void deleteBudget(String ownerSubject, UUID budgetId) {
        try {
            Budget budget = budgetRepository.findByIdAndOwnerSubject(budgetId, ownerSubject)
                    .orElseThrow(() -> new ResourceNotFoundException("Budget was not found."));

            budgetRepository.delete(budget); // relies on Budget@OneToMany cascade/orphanRemoval
            budgetRepository.flush();        // force FK check within this tx

            log.info("Deleted budgetId={}", budgetId);
        } catch (DataIntegrityViolationException ignored) {
            throw new DuplicateBudgetException(
                    "Cannot delete the budget because it is still referenced by other records.");
        }
    }

    // -------------------------
    // Kafka-used method
    // -------------------------

    /**
     * Called by KafkaConsumer.
     * Updates the budget and category spending for the accounting period containing the expense timestamp.
     */
    @Transactional
    public void trackExpense(UUID expenseId, String ownerSubject, Instant expenseTimestamp,
                             BigDecimal expenseAmount, ExpenseCategory expenseCategory) {
        trackExpense(expenseId, ownerSubject, expenseTimestamp, expenseAmount, expenseCategory, UUID.randomUUID());
    }

    @Transactional
    public void trackExpense(UUID expenseId, String ownerSubject, Instant expenseTimestamp,
                             BigDecimal expenseAmount, ExpenseCategory expenseCategory,
                             UUID sourceExpenseEventId) {
        long amountCents = policyEvaluator.toCents(expenseAmount, "Expense amount");
        if (amountCents <= 0) {
            throw new IllegalArgumentException("Expense amount must be greater than zero");
        }

        String period = periodResolver.periodFor(expenseTimestamp);
        log.info("trackExpense() called for period={} category={}", period, expenseCategory);

        Optional<Budget> budgetOpt = budgetRepository.findByOwnerSubjectAndPeriodForUpdate(ownerSubject, period);
        if (budgetOpt.isEmpty()) {
            aiCommandOutboxWriter.enqueueGeneration(
                    expenseId, ownerSubject, sourceExpenseEventId, period, amountCents, expenseCategory,
                    false, null, null, null, null, BudgetStatus.NO_BUDGET.name(),
                    "No monthly budget is set for this period.", false);
            log.info("No budget found for expense period={}; no budget row created", period);
            return;
        }

        Budget budget = budgetOpt.get();
        policyEvaluator.evaluate(budget.getMonthlyBudget(), budget.getUsedBudget(), expenseAmount);

        BigDecimal amount = policyEvaluator.fromCents(amountCents);

        // Update used
        budget.setUsedBudget(nz(budget.getUsedBudget()).add(amount));

        // Upsert category spending
        CategorySpending cs = categorySpendingRepository
                .findByBudgetIdAndCategory(budget.getId(), expenseCategory)
                .orElseGet(() -> CategorySpending.builder()
                        .budget(budget)
                        .category(expenseCategory)
                        .amountSpent(BigDecimal.ZERO)
                        .build());

        cs.setAmountSpent(nz(cs.getAmountSpent()).add(amount));

        // Persist
        budgetRepository.save(budget);
        categorySpendingRepository.save(cs);

        BudgetEvaluation finalState = policyEvaluator.evaluate(
                budget.getMonthlyBudget(), budget.getUsedBudget(), BigDecimal.ZERO);
        aiCommandOutboxWriter.enqueueGeneration(
                expenseId, budget.getOwnerSubject(), sourceExpenseEventId, budget.getPeriod(), amountCents,
                expenseCategory, finalState.budgetExists(), finalState.budgetLimitCents(),
                finalState.currentSpentCents(), finalState.remainingCentsAfter(), finalState.percentageUsed(),
                finalState.status().name(), finalState.warning(), Boolean.TRUE.equals(budget.getAlertSent()));

        log.info("Tracked expense for period={} amountCents={} category={}", period, amountCents, expenseCategory);
    }

    @Transactional
    public void reverseExpense(UUID expenseId, String ownerSubject, Instant expenseTimestamp,
                               BigDecimal expenseAmount, ExpenseCategory expenseCategory) {
        reverseExpense(expenseId, ownerSubject, expenseTimestamp, expenseAmount, expenseCategory, UUID.randomUUID());
    }

    @Transactional
    public void reverseExpense(UUID expenseId, String ownerSubject, Instant expenseTimestamp,
                               BigDecimal expenseAmount, ExpenseCategory expenseCategory,
                               UUID sourceExpenseEventId) {
        long amountCents = policyEvaluator.toCents(expenseAmount, "Expense amount");
        if (amountCents <= 0) {
            throw new IllegalArgumentException("Expense amount must be greater than zero");
        }

        String period = periodResolver.periodFor(expenseTimestamp);
        Optional<Budget> budgetOpt = budgetRepository.findByOwnerSubjectAndPeriodForUpdate(ownerSubject, period);
        if (budgetOpt.isEmpty()) {
            aiCommandOutboxWriter.enqueueDeletion(expenseId, ownerSubject, sourceExpenseEventId, period);
            log.info("No budget found while reversing expense for period={}; no budget row changed", period);
            return;
        }

        Budget budget = budgetOpt.get();
        long currentUsedCents = policyEvaluator.toCents(nz(budget.getUsedBudget()), "Current spent");
        if (currentUsedCents < amountCents) {
            throw new IllegalStateException("Cannot reverse expense beyond the recorded budget total");
        }

        CategorySpending categorySpending = categorySpendingRepository
                .findByBudgetIdAndCategory(budget.getId(), expenseCategory)
                .orElseThrow(() -> new IllegalStateException("Cannot reverse expense without category spending"));
        long currentCategoryCents = policyEvaluator.toCents(
                nz(categorySpending.getAmountSpent()), "Category spending");
        if (currentCategoryCents < amountCents) {
            throw new IllegalStateException("Cannot reverse expense beyond the recorded category total");
        }

        budget.setUsedBudget(policyEvaluator.fromCents(currentUsedCents - amountCents));
        long remainingCategoryCents = currentCategoryCents - amountCents;
        if (remainingCategoryCents == 0) {
            categorySpendingRepository.delete(categorySpending);
        } else {
            categorySpending.setAmountSpent(policyEvaluator.fromCents(remainingCategoryCents));
            categorySpendingRepository.save(categorySpending);
        }
        budgetRepository.save(budget);

        aiCommandOutboxWriter.enqueueDeletion(expenseId, ownerSubject, sourceExpenseEventId, period);

        log.info("Reversed expense for period={} amountCents={} category={}",
                period, amountCents, expenseCategory);
    }

    // -------------------------
    // Helpers
    // -------------------------

    private String normalizePeriod(String period) {
        if (period == null || period.isBlank()) return periodResolver.currentPeriod();
        if (!period.matches("^\\d{4}-(0[1-9]|1[0-2])$")) {
            throw new IllegalArgumentException("Period must be in format YYYY-MM");
        }
        return period;
    }

    private void validateBudgetLimit(BigDecimal monthlyBudget) {
        if (policyEvaluator.toCents(monthlyBudget, "Monthly budget") <= 0) {
            throw new IllegalArgumentException("Monthly budget must be greater than zero");
        }
    }

    private Budget findBudgetOr404(String ownerSubject, String period) {
        return budgetRepository.findByOwnerSubjectAndPeriod(ownerSubject, period)
                .orElseThrow(() -> new ResourceNotFoundException("No budget exists for this period."));
    }

    private BudgetResponse buildResponseWithBreakdown(Budget budget) {
        List<CategorySpending> categories = categorySpendingRepository.findAllByBudgetId(budget.getId());
        return toResponse(budget, categories);
    }

    private BudgetResponse toResponse(Budget budget, List<CategorySpending> categories) {
        BudgetEvaluation evaluation = policyEvaluator.evaluate(
                budget.getMonthlyBudget(), budget.getUsedBudget(), BigDecimal.ZERO);
        BigDecimal total = budget.getMonthlyBudget();
        BigDecimal spent = budget.getUsedBudget();
        BigDecimal remaining = policyEvaluator.fromCents(evaluation.remainingCentsAfter());

        Map<String, CategoryDetail> breakdown = categories.stream()
                .collect(Collectors.toMap(
                        cs -> cs.getCategory().name(),
                        cs -> CategoryDetail.builder()
                                .amount(nz(cs.getAmountSpent()).toPlainString())
                                .percentage(policyEvaluator.percentageFor(
                                        policyEvaluator.toCents(nz(cs.getAmountSpent()), "Category amount"),
                                        evaluation.budgetLimitCents()))
                                .build(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        return BudgetResponse.builder()
                .id(budget.getId())
                .period(budget.getPeriod())
                .monthlyBudget(total.toPlainString())
                .spent(spent.toPlainString())
                .remaining(remaining.toPlainString())
                .percentageUsed(evaluation.percentageUsed())
                .status(evaluation.status())
                .categoryBreakdown(breakdown.isEmpty() ? null : breakdown)
                .createdAt(budget.getCreatedDate())
                .updatedAt(budget.getUpdatedDate())
                .build();
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

}

