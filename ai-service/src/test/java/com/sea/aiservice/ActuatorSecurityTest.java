package com.sea.aiservice;

import com.sea.aiservice.model.AIInsight;
import com.sea.aiservice.repository.AIInsightRepository;
import com.sea.aiservice.service.InsightGenerationClient;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration,org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration,org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration,org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration,org.springframework.boot.mongodb.autoconfigure.health.MongoHealthContributorAutoConfiguration",
        "management.endpoint.health.group.readiness.include=readinessState"
})
@AutoConfigureMockMvc
@Import(ActuatorSecurityTest.TestConfigurationBeans.class)
class ActuatorSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesHealthProbesWithoutJwtAndHidesDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(result -> assertHealthEndpointStatus(result.getResponse().getStatus()))
                .andExpect(jsonPath("$.components").doesNotExist());
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(result -> assertHealthEndpointStatus(result.getResponse().getStatus()));
    }

    private void assertHealthEndpointStatus(int status) {
        assertTrue(status == 200 || status == 503);
    }

    @Test
    void doesNotExposeSensitiveActuatorEndpoints() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/actuator/beans"))
                .andExpect(status().is4xxClientError());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfigurationBeans {

        @Bean
        ConnectionFactory connectionFactory() {
            return mock(ConnectionFactory.class);
        }

        @Bean
        MongoTemplate mongoTemplate() {
            MongoTemplate template = mock(MongoTemplate.class);
            IndexOperations indexOperations = mock(IndexOperations.class);
            MongoConverter converter = mock(MongoConverter.class);
            MongoMappingContext mappingContext = mock(MongoMappingContext.class);
            when(template.findAll(org.bson.Document.class, "ai_insights")).thenReturn(List.of());
            when(template.findAll(AIInsight.class)).thenReturn(List.of());
            when(template.indexOps(AIInsight.class)).thenReturn(indexOperations);
            when(template.getConverter()).thenReturn(converter);
            doReturn(mappingContext).when(converter).getMappingContext();
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
        JwtDecoder jwtDecoder() {
            return mock(JwtDecoder.class);
        }
    }
}
