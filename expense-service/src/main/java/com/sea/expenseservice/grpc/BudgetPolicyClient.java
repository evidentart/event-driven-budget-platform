package com.sea.expenseservice.grpc;

import com.sea.budget.policy.v1.BudgetPolicyServiceGrpc;
import com.sea.budget.policy.v1.CanSpendRequest;
import com.sea.budget.policy.v1.CanSpendResponse;
import com.google.protobuf.Timestamp;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
public class BudgetPolicyClient {

    private static final Logger log = LoggerFactory.getLogger(BudgetPolicyClient.class);

    private final ManagedChannel channel;
    private final BudgetPolicyServiceGrpc.BudgetPolicyServiceBlockingStub stub;

    private final long deadlineMs;

    @Autowired
    public BudgetPolicyClient(
            @Value("${budget.service.address:budget-service}") String address,
            @Value("${budget.service.grpc.port:9001}") int port,
            @Value("${budget.service.grpc.deadline-ms:2000}") long deadlineMs
    ) {
        this(ManagedChannelBuilder.forAddress(address, port).usePlaintext().build(), deadlineMs);

        log.info("Connecting to Budget gRPC at {}:{}", address, port);
    }

    BudgetPolicyClient(ManagedChannel channel, long deadlineMs) {
        if (deadlineMs <= 0) {
            throw new IllegalArgumentException("Budget gRPC deadline must be positive");
        }

        this.deadlineMs = deadlineMs;
        this.channel = channel;
        this.stub = BudgetPolicyServiceGrpc.newBlockingStub(channel);
    }

    public BudgetAdvisory evaluate(String ownerSubject, Instant expenseTimestamp, BigDecimal amount) {
        if (ownerSubject == null || ownerSubject.isBlank()) {
            throw new IllegalArgumentException("Owner subject is required");
        }
        if (expenseTimestamp == null) {
            throw new IllegalArgumentException("Expense timestamp is required");
        }
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
            throw new IllegalArgumentException("Expense amount must be positive with at most two decimal places");
        }

        long cents = amount.multiply(BigDecimal.valueOf(100))
                .setScale(0)
                .longValueExact();

        CanSpendRequest req = CanSpendRequest.newBuilder()
                .setOwnerSubject(ownerSubject)
                .setExpenseAmountCents(cents)
                .setExpenseTimestamp(Timestamp.newBuilder()
                        .setSeconds(expenseTimestamp.getEpochSecond())
                        .setNanos(expenseTimestamp.getNano())
                        .build())
                .build();

        try {
            CanSpendResponse response = stub
                    .withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
                    .canSpend(req);

            if (!response.getAdvisoryAvailable()) {
                return BudgetAdvisory.unavailable(response.getWarning().isBlank()
                        ? "Budget status unavailable right now."
                        : response.getWarning());
            }

            if (response.getStatus().isBlank()) {
                return BudgetAdvisory.unavailable("Budget status unavailable right now.");
            }

            Long remaining = response.hasRemainingCentsAfter()
                    ? response.getRemainingCentsAfter()
                    : null;
            return new BudgetAdvisory(true, response.getStatus(), response.getWarning(), remaining);
        } catch (StatusRuntimeException e) {
            log.warn("Budget gRPC advisory unavailable status={}", e.getStatus().getCode());
            return BudgetAdvisory.unavailable("Budget status unavailable right now.");
        }
    }

    @PreDestroy
    public void shutdown() {
        channel.shutdown();
    }
}
