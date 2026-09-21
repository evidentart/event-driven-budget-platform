package com.sea.budgetservice.grpc;

import com.google.protobuf.Timestamp;
import com.sea.budget.policy.v1.CanSpendRequest;
import com.sea.budget.policy.v1.CanSpendResponse;
import com.sea.budgetservice.policy.AccountingPeriodResolver;
import com.sea.budgetservice.policy.BudgetPolicyEvaluator;
import com.sea.budgetservice.repository.BudgetRepository;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BudgetPolicyGrpcServiceTest {

    @Test
    void returnsExplicitNoBudgetAdvisoryWithoutPersistingAnything() {
        BudgetRepository repository = mock(BudgetRepository.class);
        when(repository.findByOwnerSubjectAndPeriod("alice", "2026-02"))
                .thenReturn(Optional.empty());
        BudgetPolicyGrpcService service = service(repository);
        CapturingObserver observer = new CapturingObserver();

        service.canSpend(request("alice", 1234), observer);

        assertTrue(observer.response.getAdvisoryAvailable());
        assertFalse(observer.response.getHasBudget());
        assertEquals("NO_BUDGET", observer.response.getStatus());
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsMissingExpenseTimestampAsUnavailableAdvisory() {
        BudgetRepository repository = mock(BudgetRepository.class);
        BudgetPolicyGrpcService service = service(repository);
        CapturingObserver observer = new CapturingObserver();

        service.canSpend(CanSpendRequest.newBuilder()
                .setOwnerSubject("alice")
                .setExpenseAmountCents(1234)
                .build(), observer);

        assertFalse(observer.response.getAdvisoryAvailable());
        assertEquals("INVALID_REQUEST", observer.response.getStatus());
        verifyNoInteractions(repository);
    }

    private BudgetPolicyGrpcService service(BudgetRepository repository) {
        Clock clock = Clock.fixed(Instant.parse("2026-02-10T12:00:00Z"), ZoneOffset.UTC);
        return new BudgetPolicyGrpcService(repository, new BudgetPolicyEvaluator(),
                new AccountingPeriodResolver(clock, ZoneOffset.UTC));
    }

    private CanSpendRequest request(String owner, long cents) {
        Instant timestamp = Instant.parse("2026-02-10T12:00:00Z");
        return CanSpendRequest.newBuilder()
                .setOwnerSubject(owner)
                .setExpenseAmountCents(cents)
                .setExpenseTimestamp(Timestamp.newBuilder()
                        .setSeconds(timestamp.getEpochSecond())
                        .setNanos(timestamp.getNano()))
                .build();
    }

    private static class CapturingObserver implements StreamObserver<CanSpendResponse> {
        private CanSpendResponse response;
        @Override public void onNext(CanSpendResponse value) { response = value; }
        @Override public void onError(Throwable t) { fail(t); }
        @Override public void onCompleted() { }
    }
}
