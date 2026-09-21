package com.sea.budgetservice.policy;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccountingPeriodResolverTest {

    @Test
    void resolvesExpensePeriodFromTheConfiguredAccountingZone() {
        AccountingPeriodResolver resolver = new AccountingPeriodResolver(
                Clock.fixed(Instant.parse("2026-01-01T00:30:00Z"), ZoneOffset.UTC),
                ZoneOffset.ofHours(-5));

        assertEquals("2025-12", resolver.periodFor(Instant.parse("2026-01-01T00:30:00Z")));
        assertEquals("2025-12", resolver.currentPeriod());
    }
}
