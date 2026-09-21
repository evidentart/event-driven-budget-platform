package com.sea.budgetservice.service;

import com.sea.budgetservice.dto.*;
import com.sea.budgetservice.model.Budget;
import com.sea.budgetservice.model.CategorySpending;
import com.sea.budgetservice.model.ExpenseCategory;
import com.sea.budgetservice.repository.BudgetRepository;
import com.sea.budgetservice.repository.CategorySpendingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategorySpendingRepository categorySpendingRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange.name}")
    private String rabbitExchange;

    @Value("${rabbitmq.routing.key}")
    private String rabbitRoutingKey;

    // -------------------------
    // REST-used methods
    // -------------------------

    @Transactional
    public BudgetResponse createBudget(String ownerSubject, BudgetRequest request) {
        String period = normalizePeriod(request.getPeriod());

        budgetRepository.findByOwnerSubjectAndPeriod(ownerSubject, period)
                .ifPresent(existing -> {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Budget already exists for ownerSubject=" + ownerSubject + " period=" + period
                    );
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
        String currentPeriod = Budget.getCurrentPeriod();
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
        String currentPeriod = Budget.getCurrentPeriod();
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
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Budget not found: " + budgetId
                    ));

            budgetRepository.delete(budget); // relies on Budget@OneToMany cascade/orphanRemoval
            budgetRepository.flush();        // force FK check within this tx

            log.info("Deleted budgetId={}", budgetId);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot delete budget because it is still referenced by other records.",
                    e
            );
        }
    }

    // -------------------------
    // Kafka-used method
    // -------------------------

    /**
     * Called by KafkaConsumer.
     * Updates: budget.usedBudget and category_spending for CURRENT PERIOD.
     *
     * If your Kafka event includes a real expense date/period, change `period` selection accordingly.
     */
    @Transactional
    public void trackExpense(UUID expenseId, String ownerSubject, String expensePeriod, BigDecimal expenseAmount, ExpenseCategory expenseCategory) {
        // Log inputs exactly as received from KafkaConsumer.
        log.info("trackExpense() called with expenseId={} ownerSubject={} period={} amount={} category={}",
                expenseId, ownerSubject, expensePeriod, expenseAmount, expenseCategory);

        String period = normalizePeriod(expensePeriod);

        Budget budget = getOrCreateBudgetForPeriod(ownerSubject, period);

        BigDecimal amount = nz(expenseAmount);

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

        publishBudgetCalculatedEvent(expenseId, budget, amount, expenseCategory);

        log.info("Tracked expense expenseId={} ownerSubject={} period={} amount={} category={}",
                expenseId, ownerSubject, period, amount, expenseCategory);
    }

    // -------------------------
    // Helpers
    // -------------------------

    private String normalizePeriod(String period) {
        return (period == null || period.isBlank()) ? Budget.getCurrentPeriod() : period;
    }

    private Budget findBudgetOr404(String ownerSubject, String period) {
        return budgetRepository.findByOwnerSubjectAndPeriod(ownerSubject, period)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No budget for ownerSubject=" + ownerSubject + " period=" + period
                ));
    }

    /**
     * Kafka-safe: budget missing is not a transient failure.
     * We create a default budget row (monthlyBudget=0) to prevent infinite retries.
     *
     * Also handles race conditions where multiple consumers try to create the same (ownerSubject, period)
     * if you have a unique constraint on those columns.
     */
    private Budget getOrCreateBudgetForPeriod(String ownerSubject, String period) {
        return budgetRepository.findByOwnerSubjectAndPeriod(ownerSubject, period)
                .orElseGet(() -> {
                    log.warn("No budget found for ownerSubject={} period={}; auto-creating default budget (monthlyBudget=0).",
                            ownerSubject, period);

                    Budget toCreate = Budget.builder()
                            .ownerSubject(ownerSubject)
                            .period(period)
                            .monthlyBudget(BigDecimal.ZERO)
                            .usedBudget(BigDecimal.ZERO)
                            .alertSent(Boolean.FALSE)
                            .build();

                    try {
                        return budgetRepository.save(toCreate);
                    } catch (DataIntegrityViolationException race) {
                        // Another thread/instance created it first - fetch it.
                        return budgetRepository.findByOwnerSubjectAndPeriod(ownerSubject, period)
                                .orElseThrow(() -> race);
                    }
                });
    }

    private BudgetResponse buildResponseWithBreakdown(Budget budget) {
        List<CategorySpending> categories = categorySpendingRepository.findAllByBudgetId(budget.getId());
        return toResponse(budget, categories);
    }

    private BudgetResponse toResponse(Budget budget, List<CategorySpending> categories) {
        BigDecimal total = nz(budget.getMonthlyBudget());
        BigDecimal spent = nz(budget.getUsedBudget());
        BigDecimal remaining = total.subtract(spent);

        double pctUsed = percentage(spent, total);
        BudgetStatus status = deriveStatus(pctUsed);

        Map<String, CategoryDetail> breakdown = categories.stream()
                .collect(Collectors.toMap(
                        cs -> cs.getCategory().name(),
                        cs -> CategoryDetail.builder()
                                .amount(nz(cs.getAmountSpent()))
                                .percentage(percentage(nz(cs.getAmountSpent()), total))
                                .build(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        LocalDateTime createdAt = budget.getCreatedDate();
        LocalDateTime updatedAt = budget.getUpdatedDate();

        return BudgetResponse.builder()
                .id(budget.getId())
                .period(budget.getPeriod())
                .monthlyBudget(budget.getMonthlyBudget())
                .spent(spent)
                .remaining(remaining)
                .percentageUsed(pctUsed)
                .status(status)
                .categoryBreakdown(breakdown.isEmpty() ? null : breakdown)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private double percentage(BigDecimal part, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0) return 0.0;
        return nz(part)
                .divide(total, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    private BudgetStatus deriveStatus(Double pct) {
        if (pct == null || pct < 50.0) return BudgetStatus.HEALTHY;
        if (pct < 75.0) return BudgetStatus.ON_TRACK;
        if (pct < 90.0) return BudgetStatus.CAUTION;
        if (pct <= 100.0) return BudgetStatus.NEAR_LIMIT;
        return BudgetStatus.EXCEEDED;
    }

    private void publishBudgetCalculatedEvent(UUID expenseId, Budget budget, BigDecimal expenseAmount, ExpenseCategory expenseCategory) {
        BigDecimal monthlyBudget = nz(budget.getMonthlyBudget());
        BigDecimal usedBudget = nz(budget.getUsedBudget());
        BigDecimal remainingBudget = monthlyBudget.subtract(usedBudget);
        double percentageUsed = percentage(usedBudget, monthlyBudget);
        BudgetStatus budgetStatus = deriveStatus(percentageUsed);
        String budgetWarning = buildBudgetWarning(budgetStatus, remainingBudget);

        BudgetCalculatedEvent event = BudgetCalculatedEvent.builder()
                .expenseId(expenseId)
                .ownerSubject(budget.getOwnerSubject())
                .expenseAmount(nz(expenseAmount).doubleValue())
                .expenseCategory(expenseCategory)
                .hasBudget(monthlyBudget.compareTo(BigDecimal.ZERO) > 0)
                .totalBudget(monthlyBudget.doubleValue())
                .usedBudget(usedBudget.doubleValue())
                .remainingBudget(remainingBudget.doubleValue())
                .percentageUsed(percentageUsed)
                .budgetStatus(budgetStatus.name())
                .budgetWarning(budgetWarning)
                .alertSent(Boolean.TRUE.equals(budget.getAlertSent()))
                .timestamp(LocalDateTime.now())
                .build();

        rabbitTemplate.convertAndSend(rabbitExchange, rabbitRoutingKey, event);

        log.info("Published BudgetCalculatedEvent expenseId={} ownerSubject={} exchange={} routingKey={}",
                expenseId, budget.getOwnerSubject(), rabbitExchange, rabbitRoutingKey);
    }

    private String buildBudgetWarning(BudgetStatus status, BigDecimal remainingAfter) {
        return switch (status) {
            case HEALTHY, ON_TRACK -> "";
            case CAUTION -> "Caution: you're over 75% of your monthly budget.";
            case NEAR_LIMIT -> "Near limit: you're over 90% of your monthly budget.";
            case EXCEEDED -> "Budget exceeded by " + remainingAfter.abs() + ".";
        };
    }
}

