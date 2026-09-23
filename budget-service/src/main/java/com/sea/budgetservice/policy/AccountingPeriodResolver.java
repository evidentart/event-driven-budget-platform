package com.sea.budgetservice.policy;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;

public class AccountingPeriodResolver {

    private final Clock clock;
    private final ZoneId accountingZone;

    public AccountingPeriodResolver(Clock clock, ZoneId accountingZone) {
        this.clock = clock;
        this.accountingZone = accountingZone;
    }

    public String periodFor(Instant instant) {
        if (instant == null) {
            throw new IllegalArgumentException("Timestamp is required to determine accounting period");
        }
        return YearMonth.from(instant.atZone(accountingZone)).toString();
    }

    public String currentPeriod() {
        return periodFor(Instant.now(clock));
    }
}
