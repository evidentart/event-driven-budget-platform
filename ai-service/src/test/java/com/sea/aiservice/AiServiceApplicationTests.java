package com.sea.aiservice;

import com.sea.aiservice.command.AiCommand;
import com.sea.aiservice.config.MongoConfig;
import com.sea.aiservice.config.RabbitMqConfig;
import com.sea.aiservice.model.AIInsight;
import com.sea.aiservice.repository.AIInsightRepository;
import com.sea.aiservice.service.AIInsightService;
import com.sea.aiservice.service.InsightGenerationClient;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiServiceApplicationTests {

    @Test
    void phase4ApplicationWiringConstructsWithoutExternalInfrastructure() {
        new ApplicationContextRunner()
                .withUserConfiguration(WiringConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SimpleRabbitListenerContainerFactory.class);
                    assertThat(context).hasSingleBean(MessageRecoverer.class);
                    assertThat(context).hasSingleBean(AIInsightService.class);
                    assertThat(context).hasSingleBean(AIInsightRepository.class);
                });
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Import({RabbitMqConfig.class, MongoConfig.class})
    static class WiringConfiguration {

        @Bean
        ConnectionFactory connectionFactory() {
            return mock(ConnectionFactory.class);
        }

        @Bean
        MongoTemplate mongoTemplate() {
            MongoTemplate template = mock(MongoTemplate.class);
            IndexOperations indexOperations = mock(IndexOperations.class);
            when(template.findAll(AIInsight.class)).thenReturn(List.of());
            when(template.indexOps(AIInsight.class)).thenReturn(indexOperations);
            return template;
        }

        @Bean(name = "mongoMappingContext")
        MongoMappingContext mongoMappingContext() {
            return mock(MongoMappingContext.class);
        }

        @Bean
        AIInsightRepository aiInsightRepository() {
            return mock(AIInsightRepository.class);
        }

        @Bean
        InsightGenerationClient insightGenerationClient() {
            return mock(InsightGenerationClient.class);
        }

        @Bean
        AIInsightService aiInsightService(
                InsightGenerationClient generationClient,
                AIInsightRepository repository,
                MongoTemplate mongoTemplate) {
            return new AIInsightService(generationClient, repository, mongoTemplate);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return mock(JwtDecoder.class);
        }
    }
}
