package com.sea.expenseservice.grpc;

import com.google.protobuf.Timestamp;
import com.sea.budget.policy.v1.BudgetPolicyServiceGrpc;
import com.sea.budget.policy.v1.CanSpendResponse;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BudgetPolicyClientTest {

    private Server server;
    private ManagedChannel channel;
    private BudgetPolicyClient client;

    @BeforeEach
    void setUp() throws Exception {
        String serverName = "budget-policy-" + UUID.randomUUID();
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(new BudgetPolicyServiceGrpc.BudgetPolicyServiceImplBase() {
                    @Override
                    public void canSpend(
                            com.sea.budget.policy.v1.CanSpendRequest request,
                            io.grpc.stub.StreamObserver<CanSpendResponse> observer) {
                        assertEquals("alice", request.getOwnerSubject());
                        assertEquals(1_234L, request.getExpenseAmountCents());
                        assertEquals(Instant.parse("2026-02-18T18:30:00Z").getEpochSecond(),
                                request.getExpenseTimestamp().getSeconds());
                        assertEquals(0, request.getExpenseTimestamp().getNanos());
                        observer.onNext(CanSpendResponse.newBuilder()
                                .setAdvisoryAvailable(true)
                                .setHasBudget(true)
                                .setStatus("ON_TRACK")
                                .setWarning("")
                                .setRemainingCentsAfter(8_766L)
                                .build());
                        observer.onCompleted();
                    }
                })
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        client = new BudgetPolicyClient(channel, 1_000L);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow();
        server.shutdownNow();
        server.awaitTermination();
    }

    @Test
    void convertsDecimalAndInstantAcrossGrpcBoundary() {
        BudgetAdvisory advisory = client.evaluate(
                "alice",
                Instant.parse("2026-02-18T18:30:00Z"),
                new BigDecimal("12.34"));

        assertTrue(advisory.available());
        assertEquals("ON_TRACK", advisory.status());
        assertEquals(8_766L, advisory.remainingCentsAfter());
    }
}
