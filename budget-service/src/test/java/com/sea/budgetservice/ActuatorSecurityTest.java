package com.sea.budgetservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "grpc.server.port=0",
        "spring.datasource.url=jdbc:h2:mem:budget_actuator_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.kafka.listener.auto-startup=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
@AutoConfigureMockMvc
class ActuatorSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesHealthProbesWithoutJwtAndHidesDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(result -> assertTrue(
                        result.getResponse().getStatus() == 200 || result.getResponse().getStatus() == 503))
                .andExpect(jsonPath("$.components").doesNotExist());
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk());
    }

    @Test
    void doesNotExposeSensitiveActuatorEndpoints() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/actuator/beans"))
                .andExpect(status().is4xxClientError());
    }
}
