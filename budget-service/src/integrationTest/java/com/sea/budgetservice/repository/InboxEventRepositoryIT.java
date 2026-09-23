package com.sea.budgetservice.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Testcontainers
class InboxEventRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private InboxEventRepository repository;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Test
    void insertIfAbsentUsesPostgresConflictHandlingForDuplicateEvents() {
        UUID eventId = UUID.randomUUID();
        Instant receivedAt = Instant.parse("2026-02-18T18:30:00Z");

        assertEquals(1, repository.insertIfAbsent(
                UUID.randomUUID(), eventId, "EXPENSE_CREATED", 1, receivedAt));
        assertEquals(0, repository.insertIfAbsent(
                UUID.randomUUID(), eventId, "EXPENSE_CREATED", 1, receivedAt));
        assertEquals(1, repository.count());
    }
}
