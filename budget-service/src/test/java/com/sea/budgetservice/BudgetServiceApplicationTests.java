package com.sea.budgetservice;

import com.sea.budgetservice.config.RabbitMqConfig;
import com.sea.budgetservice.config.TimeConfig;
import com.sea.budgetservice.model.AiCommandOutbox;
import com.sea.budgetservice.repository.AiCommandOutboxRepository;
import com.sea.budgetservice.repository.BudgetRepository;
import com.sea.budgetservice.repository.CategorySpendingRepository;
import com.sea.budgetservice.service.AiCommandOutboxPublisher;
import com.sea.budgetservice.service.AiCommandOutboxWriter;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BudgetServiceApplicationTests {

    @Test
    void phase4ApplicationWiringConstructsWithoutExternalInfrastructure() {
        new ApplicationContextRunner()
                .withUserConfiguration(WiringConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RabbitTemplate.class);
                    assertThat(context).hasSingleBean(AiCommandOutboxWriter.class);
                    assertThat(context).hasSingleBean(AiCommandOutboxPublisher.class);
                });
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Import({RabbitMqConfig.class, TimeConfig.class,
            AiCommandOutboxWriter.class, AiCommandOutboxPublisher.class})
    static class WiringConfiguration {

        @Bean
        ConnectionFactory connectionFactory() {
            return mock(ConnectionFactory.class);
        }

        @Bean
        AiCommandOutboxRepository aiCommandOutboxRepository() {
            return mock(AiCommandOutboxRepository.class);
        }

        @Bean
        BudgetRepository budgetRepository() {
            return mock(BudgetRepository.class);
        }

        @Bean
        CategorySpendingRepository categorySpendingRepository() {
            return mock(CategorySpendingRepository.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

    }
}
