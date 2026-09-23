package com.sea.budgetservice.grpc;

import com.google.protobuf.Timestamp;
import com.sea.budget.policy.v1.BudgetPolicyServiceGrpc;
import com.sea.budget.policy.v1.CanSpendRequest;
import com.sea.budget.policy.v1.CanSpendResponse;
import com.sea.budgetservice.model.Budget;
import com.sea.budgetservice.policy.AccountingPeriodResolver;
import com.sea.budgetservice.policy.BudgetEvaluation;
import com.sea.budgetservice.policy.BudgetPolicyEvaluator;
import com.sea.budgetservice.repository.BudgetRepository;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class BudgetPolicyGrpcService extends BudgetPolicyServiceGrpc.BudgetPolicyServiceImplBase {

    private final BudgetRepository budgetRepository;
    private final BudgetPolicyEvaluator policyEvaluator;
    private final AccountingPeriodResolver periodResolver;

    @Override
    public void canSpend(CanSpendRequest request, StreamObserver<CanSpendResponse> responseObserver) {
        try {
            if (request.getOwnerSubject().isBlank()) {
                respond(responseObserver, unavailable("INVALID_REQUEST", "Owner subject is required."));
                return;
            }
            if (!request.hasExpenseTimestamp() || request.getExpenseTimestamp().getNanos() < 0
                    || request.getExpenseTimestamp().getNanos() > 999_999_999) {
                respond(responseObserver, unavailable("INVALID_REQUEST", "Expense timestamp is required and must be valid."));
                return;
            }

            Instant expenseTimestamp = toInstant(request.getExpenseTimestamp());
            long amountCents = request.getExpenseAmountCents();
            if (amountCents <= 0) {
                respond(responseObserver, unavailable("INVALID_REQUEST", "Expense amount must be greater than zero."));
                return;
            }

            String period = periodResolver.periodFor(expenseTimestamp);
            Optional<Budget> budgetOpt = budgetRepository.findByOwnerSubjectAndPeriod(
                    request.getOwnerSubject(), period);

            if (budgetOpt.isEmpty()) {
                respond(responseObserver, CanSpendResponse.newBuilder()
                        .setAdvisoryAvailable(true)
                        .setHasBudget(false)
                        .setStatus("NO_BUDGET")
                        .setWarning("No monthly budget is set for this period.")
                        .build());
                return;
            }

            Budget budget = budgetOpt.get();
            BudgetEvaluation evaluation = policyEvaluator.evaluate(
                    budget.getMonthlyBudget(),
                    budget.getUsedBudget(),
                    policyEvaluator.fromCents(amountCents));

            CanSpendResponse.Builder response = CanSpendResponse.newBuilder()
                    .setAdvisoryAvailable(true)
                    .setHasBudget(true)
                    .setBudgetLimitCents(evaluation.budgetLimitCents())
                    .setCurrentSpentCents(evaluation.currentSpentCents())
                    .setProjectedSpentCents(evaluation.projectedSpentCents())
                    .setRemainingCentsAfter(evaluation.remainingCentsAfter())
                    .setPercentageBasisPoints(evaluation.percentageUsed()
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(0, RoundingMode.HALF_UP)
                            .intValueExact())
                    .setStatus(evaluation.status().name())
                    .setWarning(evaluation.warning());

            respond(responseObserver, response.build());
        } catch (IllegalArgumentException | ArithmeticException e) {
            respond(responseObserver, unavailable("INVALID_REQUEST", e.getMessage()));
        } catch (Exception e) {
            log.error("Budget policy advisory evaluation failed", e);
            respond(responseObserver, unavailable("UNAVAILABLE", "Budget status unavailable right now."));
        }
    }

    private Instant toInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    private CanSpendResponse unavailable(String status, String warning) {
        return CanSpendResponse.newBuilder()
                .setAdvisoryAvailable(false)
                .setHasBudget(false)
                .setStatus(status)
                .setWarning(warning == null || warning.isBlank()
                        ? "Budget status unavailable right now."
                        : warning)
                .build();
    }

    private void respond(StreamObserver<CanSpendResponse> observer, CanSpendResponse response) {
        observer.onNext(response);
        observer.onCompleted();
    }
}
