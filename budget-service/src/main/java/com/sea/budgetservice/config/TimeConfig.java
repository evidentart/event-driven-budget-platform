package com.sea.budgetservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import com.sea.budgetservice.policy.AccountingPeriodResolver;
import com.sea.budgetservice.policy.BudgetPolicyEvaluator;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class TimeConfig {

    @Bean
    Clock applicationClock() {
        return Clock.systemUTC();
    }

    @Bean
    AccountingPeriodResolver accountingPeriodResolver(
            Clock clock,
            @Value("${app.accounting.time-zone:UTC}") String accountingTimeZone) {
        return new AccountingPeriodResolver(clock, ZoneId.of(accountingTimeZone));
    }

    @Bean
    BudgetPolicyEvaluator budgetPolicyEvaluator() {
        return new BudgetPolicyEvaluator();
    }
}
