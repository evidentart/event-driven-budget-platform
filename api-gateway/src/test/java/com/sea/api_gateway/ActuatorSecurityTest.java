package com.sea.api_gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActuatorSecurityTest {

    private WebTestClient webTestClient;

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void exposesHealthProbesWithoutJwtAndHidesDetails() {
        webTestClient.get().uri("/actuator/health").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.components").doesNotExist();
        webTestClient.get().uri("/actuator/health/liveness").exchange()
                .expectStatus().isOk();
        webTestClient.get().uri("/actuator/health/readiness").exchange()
                .expectStatus().isOk();
    }

    @Test
    void doesNotExposeSensitiveActuatorEndpoints() {
        webTestClient.get().uri("/actuator/env").exchange()
                .expectStatus().is4xxClientError();
        webTestClient.get().uri("/actuator/beans").exchange()
                .expectStatus().is4xxClientError();
    }
}
