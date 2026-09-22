package com.sea.aiservice.config;

import com.sea.aiservice.model.AIInsight;
import com.sea.aiservice.model.InsightLifecycleStatus;
import org.bson.Document;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

import jakarta.annotation.PostConstruct;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Configuration
@EnableMongoAuditing
public class MongoConfig {

    private static final String INSIGHT_COLLECTION = "ai_insights";

    private final MongoTemplate mongoTemplate;

    public MongoConfig(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void migrateAndEnsureInsightIndex() {
        List<AIInsight> documents = readDocumentsForMigration();
        List<String> validationErrors = documents.stream()
                .map(this::validateForMigration)
                .flatMap(List::stream)
                .toList();
        if (!validationErrors.isEmpty()) {
            throw new IllegalStateException(
                    "AI insight migration cannot safely continue; no documents were changed. "
                            + "Fix or quarantine these documents before restarting: "
                            + String.join("; ", validationErrors));
        }

        MigrationPlan plan = buildPlan(documents);
        applyPlan(plan);
        mongoTemplate.indexOps(AIInsight.class).ensureIndex(
                new CompoundIndexDefinition(new Document("ownerSubject", 1)
                        .append("expenseId", 1)
                        .append("generation", 1))
                        .unique()
                        .named("uk_ai_insight_owner_expense_generation"));
    }

    private List<AIInsight> readDocumentsForMigration() {
        List<Document> rawDocuments;
        try {
            rawDocuments = mongoTemplate.findAll(Document.class, INSIGHT_COLLECTION);
        } catch (RuntimeException failure) {
            throw migrationFailure(List.of(
                    "unable to read raw collection " + INSIGHT_COLLECTION
                            + " (" + failure.getClass().getSimpleName() + ")"), failure);
        }

        // An empty raw result is also kept compatible with the existing typed read path.
        // For a non-empty collection, every document is converted only after the raw scan,
        // so mapping failures are reported before any migration mutation can occur.
        if (rawDocuments == null || rawDocuments.isEmpty()) {
            return mongoTemplate.findAll(AIInsight.class);
        }

        MongoConverter converter = mongoTemplate.getConverter();
        List<AIInsight> converted = new java.util.ArrayList<>();
        List<String> conversionErrors = new java.util.ArrayList<>();
        for (Document rawDocument : rawDocuments) {
            try {
                converted.add(converter.read(AIInsight.class, rawDocument));
            } catch (RuntimeException failure) {
                conversionErrors.add(rawDocumentContext(rawDocument)
                        + " cannot be converted to AIInsight ("
                        + failure.getClass().getSimpleName() + ")");
            }
        }
        if (!conversionErrors.isEmpty()) {
            throw migrationFailure(conversionErrors, null);
        }
        return converted;
    }

    private IllegalStateException migrationFailure(List<String> errors, Throwable cause) {
        return new IllegalStateException(
                "AI insight migration cannot safely continue; no documents were changed. "
                        + "Inspect or quarantine the reported legacy records before restarting: "
                        + String.join("; ", errors), cause);
    }

    private String rawDocumentContext(Document rawDocument) {
        if (rawDocument == null) {
            return "raw document <null>";
        }
        Object rawId = rawDocument.get("_id");
        return "raw document _id=" + (rawId == null ? "<missing>" : rawId)
                + ", fields=" + rawDocument.keySet();
    }

    private List<String> validateForMigration(AIInsight insight) {
        String documentId = insight == null ? "<null>" : String.valueOf(insight.getId());
        if (insight == null) {
            return List.of("document " + documentId + " is null");
        }

        List<String> errors = new java.util.ArrayList<>();
        if (insight.getId() == null || insight.getId().isBlank()) {
            errors.add("document " + documentId + " has no Mongo id");
        }
        if (insight.getOwnerSubject() == null || insight.getOwnerSubject().isBlank()) {
            errors.add("document " + documentId + " has no ownerSubject");
        }
        if (insight.getExpenseId() == null) {
            errors.add("document " + documentId + " has no expenseId");
        }
        if (insight.getGeneration() != null && insight.getGeneration() < 1) {
            errors.add("document " + documentId + " has invalid generation=" + insight.getGeneration());
        }

        if (insight.getLifecycleStatus() != InsightLifecycleStatus.DELETED
                && (insight.getBudgetSummaryMessage() == null
                || insight.getBudgetSummaryMessage().isBlank()
                || insight.getSpendingImprovements() == null
                || insight.getSavingSuggestions() == null)) {
            errors.add("document " + documentId
                    + " is not a DELETED tombstone and lacks summary/improvement/saving fields");
        }
        return errors;
    }

    private MigrationPlan buildPlan(List<AIInsight> documents) {
        Map<LogicalKey, List<AIInsight>> groups = new LinkedHashMap<>();
        for (AIInsight document : documents) {
            int effectiveGeneration = document.getGeneration() == null ? 1 : document.getGeneration();
            groups.computeIfAbsent(
                    new LogicalKey(document.getOwnerSubject(), document.getExpenseId(), effectiveGeneration),
                    ignored -> new java.util.ArrayList<>()).add(document);
        }

        List<AIInsight> winners = groups.values().stream()
                .map(this::newestDocument)
                .toList();
        List<AIInsight> duplicates = groups.values().stream()
                .flatMap(List::stream)
                .filter(document -> winners.stream().noneMatch(winner -> Objects.equals(winner.getId(), document.getId())))
                .toList();
        return new MigrationPlan(winners, duplicates);
    }

    private AIInsight newestDocument(List<AIInsight> group) {
        return group.stream()
                .max(Comparator.comparing(AIInsight::getUpdatedAt,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(AIInsight::getCreatedAt,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(AIInsight::getId))
                .orElseThrow(() -> new IllegalStateException("Empty AI insight migration group"));
    }

    private void applyPlan(MigrationPlan plan) {
        plan.winners().forEach(this::backfillWinner);
        plan.duplicates().forEach(mongoTemplate::remove);
    }

    private void backfillWinner(AIInsight winner) {
        int generation = winner.getGeneration() == null ? 1 : winner.getGeneration();
        boolean changed = false;
        if (winner.getGeneration() == null) {
            winner.setGeneration(generation);
            changed = true;
        }
        if (winner.getLifecycleStatus() == null) {
            winner.setLifecycleStatus(InsightLifecycleStatus.ACTIVE);
            changed = true;
        }
        String logicalKey = logicalKey(winner.getOwnerSubject(), winner.getExpenseId(), generation);
        if (!Objects.equals(winner.getLogicalKey(), logicalKey)) {
            winner.setLogicalKey(logicalKey);
            changed = true;
        }
        if (changed) {
            mongoTemplate.save(winner);
        }
    }

    private String logicalKey(String ownerSubject, UUID expenseId, int generation) {
        return ownerSubject + ":" + expenseId + ":" + generation;
    }

    private record LogicalKey(String ownerSubject, UUID expenseId, int generation) {
    }

    private record MigrationPlan(List<AIInsight> winners, List<AIInsight> duplicates) {
    }
}
