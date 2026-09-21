package com.sea.expenseservice.grpc;

import com.sea.budget.policy.v1.BudgetPolicyServiceGrpc;
import com.sea.budget.policy.v1.CanSpendRequest;
import com.sea.budget.policy.v1.CanSpendResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.TimeUnit;

@Service
public class BudgetPolicyClient {

    private static final Logger log = LoggerFactory.getLogger(BudgetPolicyClient.class);

    private final ManagedChannel channel;
    private final BudgetPolicyServiceGrpc.BudgetPolicyServiceBlockingStub stub;

    private final long deadlineMs;

    public BudgetPolicyClient(
            @Value("${budget.service.address:budget-service}") String address,
            @Value("${budget.service.grpc.port:9001}") int port,
            @Value("${budget.service.grpc.deadline-ms:2000}") long deadlineMs
    ) {
        this.deadlineMs = deadlineMs;

        log.info("Connecting to Budget gRPC at {}:{}", address, port);

        this.channel = ManagedChannelBuilder
                .forAddress(address, port)
                .usePlaintext()
                .build();

        this.stub = BudgetPolicyServiceGrpc.newBlockingStub(channel);
    }

    public CanSpendResponse canSpend(String ownerSubject, String period, String category, BigDecimal amount) {
        long cents = amount.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        CanSpendRequest req = CanSpendRequest.newBuilder()
                .setOwnerSubject(ownerSubject)
                .setPeriod(period)
                .setCategory(category)
                .setAmountCents(cents)
                .build();

        try {
            return stub
                    .withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
                    .canSpend(req);
        } catch (StatusRuntimeException e) {
            // Let ExpenseService decide fallback (Option B continues).
            log.warn("Budget gRPC call failed status={} message={}", e.getStatus(), e.getMessage());
            throw e;
        }
    }

    @PreDestroy
    public void shutdown() {
        channel.shutdown();
    }
}
