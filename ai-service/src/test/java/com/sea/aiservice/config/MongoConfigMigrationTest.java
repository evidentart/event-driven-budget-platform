package com.sea.aiservice.config;

import com.sea.aiservice.model.AIInsight;
import com.sea.aiservice.model.InsightLifecycleStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.index.IndexOperations;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MongoConfigMigrationTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private IndexOperations indexOperations;

    @Mock
    private MongoConverter mongoConverter;

    @Test
    void malformedRawDocumentFailsWithActionableMigrationErrorBeforeCleanup() {
        org.bson.Document malformed = new org.bson.Document("_id", "raw-invalid-uuid")
                .append("ownerSubject", "alice")
                .append("expenseId", "not-a-uuid")
                .append("generation", 1);
        when(mongoTemplate.findAll(org.bson.Document.class, "ai_insights"))
                .thenReturn(List.of(malformed));
        when(mongoTemplate.getConverter()).thenReturn(mongoConverter);
        when(mongoConverter.read(AIInsight.class, malformed))
                .thenThrow(new IllegalArgumentException("invalid UUID"));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> config().migrateAndEnsureInsightIndex());

        org.junit.jupiter.api.Assertions.assertTrue(failure.getMessage().contains(
                "AI insight migration cannot safely continue"));
        org.junit.jupiter.api.Assertions.assertTrue(failure.getMessage().contains(
                "raw-invalid-uuid"));
        verify(mongoTemplate, never()).findAll(AIInsight.class);
        verify(mongoTemplate, never()).save(any(AIInsight.class));
        verify(mongoTemplate, never()).remove(any(AIInsight.class));
        verify(mongoTemplate, never()).indexOps(AIInsight.class);
    }

    @Test
    void allInvalidLegacyGroupFailsBeforeAnyDestructiveOperation() {
        AIInsight invalid = AIInsight.builder()
                .id("invalid")
                .ownerSubject("alice")
                .expenseId(UUID.randomUUID())
                .build();
        when(mongoTemplate.findAll(AIInsight.class)).thenReturn(List.of(invalid));

        assertThrows(IllegalStateException.class, () -> config().migrateAndEnsureInsightIndex());

        verify(mongoTemplate, never()).save(any(AIInsight.class));
        verify(mongoTemplate, never()).remove(any(AIInsight.class));
        verify(mongoTemplate, never()).indexOps(AIInsight.class);
    }

    @Test
    void mixedValidAndInvalidDuplicatesFailWithoutDeletingInvalidData() {
        UUID expenseId = UUID.randomUUID();
        AIInsight valid = validLegacy("valid", expenseId, Instant.parse("2026-01-02T00:00:00Z"));
        AIInsight invalid = AIInsight.builder()
                .id("invalid")
                .ownerSubject("alice")
                .expenseId(expenseId)
                .createdAt(Instant.parse("2026-01-03T00:00:00Z"))
                .build();
        when(mongoTemplate.findAll(AIInsight.class)).thenReturn(List.of(valid, invalid));

        assertThrows(IllegalStateException.class, () -> config().migrateAndEnsureInsightIndex());

        verify(mongoTemplate, never()).save(any(AIInsight.class));
        verify(mongoTemplate, never()).remove(any(AIInsight.class));
        verify(mongoTemplate, never()).indexOps(AIInsight.class);
    }

    @Test
    void validLegacyDuplicatesAreDeduplicatedAndBackfilledToGenerationOne() {
        UUID expenseId = UUID.randomUUID();
        AIInsight older = validLegacy("older", expenseId, Instant.parse("2026-01-01T00:00:00Z"));
        AIInsight newer = validLegacy("newer", expenseId, Instant.parse("2026-01-02T00:00:00Z"));
        prepare(List.of(older, newer));

        config().migrateAndEnsureInsightIndex();

        assertEquals(1, newer.getGeneration());
        assertEquals(InsightLifecycleStatus.ACTIVE, newer.getLifecycleStatus());
        assertEquals("alice:" + expenseId + ":1", newer.getLogicalKey());
        verify(mongoTemplate).save(same(newer));
        verify(mongoTemplate).remove(same(older));
        verify(indexOperations).ensureIndex(any());
    }

    @Test
    void alreadyMigratedGenerationOneIsNotModifiedOnStartup() {
        UUID expenseId = UUID.randomUUID();
        AIInsight current = validActive("current", expenseId, 1);
        current.setLogicalKey("alice:" + expenseId + ":1");
        prepare(List.of(current));

        config().migrateAndEnsureInsightIndex();

        verify(mongoTemplate, never()).save(any(AIInsight.class));
        verify(mongoTemplate, never()).remove(any(AIInsight.class));
        verify(indexOperations).ensureIndex(any());
    }

    @Test
    void distinctGenerationsForOneExpenseRemainDistinct() {
        UUID expenseId = UUID.randomUUID();
        AIInsight generationOne = validActive("generation-one", expenseId, 1);
        generationOne.setLogicalKey("alice:" + expenseId + ":1");
        AIInsight generationTwo = validActive("generation-two", expenseId, 2);
        generationTwo.setLogicalKey("alice:" + expenseId + ":2");
        prepare(List.of(generationOne, generationTwo));

        config().migrateAndEnsureInsightIndex();

        assertEquals(1, generationOne.getGeneration());
        assertEquals(2, generationTwo.getGeneration());
        verify(mongoTemplate, never()).save(any(AIInsight.class));
        verify(mongoTemplate, never()).remove(any(AIInsight.class));
    }

    @Test
    void repeatedMigrationIsIdempotent() {
        UUID expenseId = UUID.randomUUID();
        AIInsight winner = validLegacy("winner", expenseId, Instant.parse("2026-01-02T00:00:00Z"));
        AIInsight duplicate = validLegacy("duplicate", expenseId, Instant.parse("2026-01-01T00:00:00Z"));
        when(mongoTemplate.findAll(org.bson.Document.class, "ai_insights"))
                .thenReturn(List.of(), List.of());
        when(mongoTemplate.findAll(AIInsight.class)).thenReturn(
                List.of(winner, duplicate), List.of(winner));
        when(mongoTemplate.indexOps(AIInsight.class)).thenReturn(indexOperations);

        MongoConfig config = config();
        config.migrateAndEnsureInsightIndex();
        config.migrateAndEnsureInsightIndex();

        verify(mongoTemplate, times(1)).save(same(winner));
        verify(mongoTemplate, times(1)).remove(same(duplicate));
        verify(indexOperations, times(2)).ensureIndex(any());
    }

    @Test
    void preservesDeletedTombstoneState() {
        UUID expenseId = UUID.randomUUID();
        AIInsight tombstone = AIInsight.builder()
                .id("tombstone")
                .ownerSubject("alice")
                .expenseId(expenseId)
                .generation(1)
                .logicalKey("alice:" + expenseId + ":1")
                .lifecycleStatus(InsightLifecycleStatus.DELETED)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        prepare(List.of(tombstone));

        config().migrateAndEnsureInsightIndex();

        assertEquals(InsightLifecycleStatus.DELETED, tombstone.getLifecycleStatus());
        assertEquals(1, tombstone.getGeneration());
        verify(mongoTemplate, never()).save(any(AIInsight.class));
        verify(mongoTemplate, never()).remove(any(AIInsight.class));
    }

    private MongoConfig config() {
        return new MongoConfig(mongoTemplate);
    }

    private void prepare(List<AIInsight> documents) {
        when(mongoTemplate.findAll(org.bson.Document.class, "ai_insights"))
                .thenReturn(List.of());
        when(mongoTemplate.findAll(AIInsight.class)).thenReturn(documents);
        when(mongoTemplate.indexOps(AIInsight.class)).thenReturn(indexOperations);
    }

    private AIInsight validLegacy(String id, UUID expenseId, Instant createdAt) {
        return AIInsight.builder()
                .id(id)
                .ownerSubject("alice")
                .expenseId(expenseId)
                .budgetSummaryMessage("Summary")
                .spendingImprovements(List.of("one", "two", "three"))
                .savingSuggestions(List.of("one", "two", "three"))
                .createdAt(createdAt)
                .build();
    }

    private AIInsight validActive(String id, UUID expenseId, int generation) {
        AIInsight insight = validLegacy(id, expenseId, Instant.parse("2026-01-01T00:00:00Z"));
        insight.setGeneration(generation);
        insight.setLifecycleStatus(InsightLifecycleStatus.ACTIVE);
        return insight;
    }
}
