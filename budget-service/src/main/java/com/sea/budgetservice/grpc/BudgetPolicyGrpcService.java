package com.sea.budgetservice.grpc;

import com.sea.budget.policy.v1.BudgetPolicyServiceGrpc;
import com.sea.budget.policy.v1.CanSpendRequest;
import com.sea.budget.policy.v1.CanSpendResponse;
import com.sea.budgetservice.dto.BudgetStatus;
import com.sea.budgetservice.model.Budget;
import com.sea.budgetservice.repository.BudgetRepository;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class BudgetPolicyGrpcService extends BudgetPolicyServiceGrpc.BudgetPolicyServiceImplBase {

    private final BudgetRepository budgetRepository;

    @Override
    public void canSpend(CanSpendRequest request, StreamObserver<CanSpendResponse> responseObserver) {
        try {
            String ownerSubject = request.getOwnerSubject();
            if (ownerSubject == null || ownerSubject.isBlank()) {
                responseObserver.onNext(CanSpendResponse.newBuilder()
                        .setHasBudget(false)
                        .setAllowed(true) // Option B: still allow
                        .setRemainingCentsAfter(0)
                        .setStatus("UNKNOWN")
                        .setWarning("Missing owner subject.")
                        .build());
                responseObserver.onCompleted();
                return;
            }

            String period = request.getPeriod();
            Optional<Budget> budgetOpt = budgetRepository.findByOwnerSubjectAndPeriod(ownerSubject, period);

            if (budgetOpt.isEmpty()) {
                responseObserver.onNext(CanSpendResponse.newBuilder()
                        .setHasBudget(false)
                        .setAllowed(true)
                        .setRemainingCentsAfter(0)
                        .setStatus("NO_BUDGET")
                        .setWarning("No budget found for " + period + ". Consider creating a monthly budget.")
                        .build());
                responseObserver.onCompleted();
                return;
            }

            Budget budget = budgetOpt.get();

            BigDecimal amount = centsToBigDecimal(request.getAmountCents());
            BigDecimal newUsed = safe(budget.getUsedBudget()).add(amount);
            BigDecimal remainingAfter = safe(budget.getMonthlyBudget()).subtract(newUsed);

            double pctUsedAfter = computePercentUsed(newUsed, budget.getMonthlyBudget());
            BudgetStatus status = toStatus(pctUsedAfter);

            String warning = buildWarning(status, remainingAfter);

            responseObserver.onNext(CanSpendResponse.newBuilder()
                    .setHasBudget(true)
                    .setAllowed(true) // Option B: always allow
                    .setRemainingCentsAfter(bigDecimalToCents(remainingAfter))
                    .setStatus(status.name())
                    .setWarning(warning)
                    .build());

            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Budget policy gRPC evaluation failed for ownerSubject={} period={}",
                    request.getOwnerSubject(), request.getPeriod(), e);
            // Never fail the RPC in a way that breaks expense creation flow.
            responseObserver.onNext(CanSpendResponse.newBuilder()
                    .setHasBudget(false)
                    .setAllowed(true) // Option B: allow
                    .setRemainingCentsAfter(0)
                    .setStatus("UNKNOWN")
                    .setWarning("Budget policy check error.")
                    .build());
            responseObserver.onCompleted();
        }
    }


    private static BigDecimal safe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal centsToBigDecimal(long cents) {
        return BigDecimal.valueOf(cents)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private static long bigDecimalToCents(BigDecimal amount) {
        // remaining can be negative; keep sign
        return amount.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    private static double computePercentUsed(BigDecimal used, BigDecimal monthly) {
        if (monthly == null || monthly.compareTo(BigDecimal.ZERO) <= 0) return 0.0;
        return used.divide(monthly, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    private static BudgetStatus toStatus(double percentageUsed) {
        if (percentageUsed < 50.0) return BudgetStatus.HEALTHY;
        if (percentageUsed < 75.0) return BudgetStatus.ON_TRACK;
        if (percentageUsed < 90.0) return BudgetStatus.CAUTION;
        if (percentageUsed <= 100.0) return BudgetStatus.NEAR_LIMIT;
        return BudgetStatus.EXCEEDED;
    }

    private static String buildWarning(BudgetStatus status, BigDecimal remainingAfter) {
        return switch (status) {
            case HEALTHY, ON_TRACK -> "";
            case CAUTION -> "Caution: you're over 75% of your monthly budget.";
            case NEAR_LIMIT -> "Near limit: you're over 90% of your monthly budget.";
            case EXCEEDED -> "Budget exceeded by " + remainingAfter.abs() + ".";
        };
    }
}
