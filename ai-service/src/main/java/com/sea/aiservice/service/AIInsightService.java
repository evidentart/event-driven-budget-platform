package com.sea.aiservice.service;

import com.sea.aiservice.command.AiCommand;
import com.sea.aiservice.dto.GeneratedInsightResponse;
import com.sea.aiservice.dto.InsightGenerationRequest;
import com.sea.aiservice.exception.InvalidGeneratedInsightException;
import com.sea.aiservice.model.AIInsight;
import com.sea.aiservice.model.InsightLifecycleStatus;
import com.sea.aiservice.model.SeverityLevel;
import com.sea.aiservice.repository.AIInsightRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AIInsightService {

    private static final int GENERATION = 1;
    private static final int MAX_TEXT_LENGTH = 2_000;

    private final InsightGenerationClient generationClient;
    private final AIInsightRepository repository;
    private final MongoTemplate mongoTemplate;

    public void processGenerate(AiCommand command) {
        AIInsight existing = repository.findByOwnerSubjectAndExpenseIdAndGeneration(
                command.getOwnerSubject(), command.getExpenseId(), GENERATION).orElse(null);
        if (existing != null) {
            if (existing.getLifecycleStatus() == InsightLifecycleStatus.DELETED) {
                log.info("Ignoring stale AI generation commandId={} for deleted expenseId={}",
                        command.getCommandId(), command.getExpenseId());
            } else {
                log.info("Ignoring duplicate AI generation commandId={} for expenseId={}",
                        command.getCommandId(), command.getExpenseId());
            }
            return;
        }

        GeneratedInsightResponse generated = generationClient.generate(new InsightGenerationRequest(command));
        validate(generated);

        Instant now = Instant.now();
        String logicalKey = logicalKey(command);
        Query activeOrMissing = Query.query(new Criteria().andOperator(
                Criteria.where("ownerSubject").is(command.getOwnerSubject()),
                Criteria.where("expenseId").is(command.getExpenseId()),
                Criteria.where("generation").is(GENERATION),
                new Criteria().orOperator(
                        Criteria.where("lifecycleStatus").ne(InsightLifecycleStatus.DELETED),
                        Criteria.where("lifecycleStatus").exists(false)
                )
        ));
        Update update = new Update()
                .set("ownerSubject", command.getOwnerSubject())
                .set("expenseId", command.getExpenseId())
                .set("generation", GENERATION)
                .set("logicalKey", logicalKey)
                .set("lifecycleStatus", InsightLifecycleStatus.ACTIVE)
                .set("commandId", command.getCommandId())
                .set("sourceExpenseEventId", command.getSourceExpenseEventId())
                .set("expenseCategory", command.getExpenseCategory())
                .set("severity", determineSeverity(command))
                .set("budgetSummaryMessage", generated.getBudgetSummaryMessage())
                .set("spendingImprovements", flattenImprovements(generated))
                .set("savingSuggestions", flattenSuggestions(generated))
                .set("budgetWarnings", mergeWarnings(command, generated))
                .set("updatedAt", now)
                .setOnInsert("createdAt", now);

        try {
            mongoTemplate.findAndModify(
                    activeOrMissing, update,
                    FindAndModifyOptions.options().upsert(true).returnNew(true), AIInsight.class);
            log.info("Saved AI insight commandId={} expenseId={}", command.getCommandId(), command.getExpenseId());
        } catch (DuplicateKeyException duplicate) {
            handleExpectedDuplicate(command);
        }
    }

    public void processDelete(AiCommand command) {
        Instant now = Instant.now();
        Query key = Query.query(new Criteria().andOperator(
                Criteria.where("ownerSubject").is(command.getOwnerSubject()),
                Criteria.where("expenseId").is(command.getExpenseId()),
                Criteria.where("generation").is(GENERATION)
        ));
        Update tombstone = new Update()
                .set("ownerSubject", command.getOwnerSubject())
                .set("expenseId", command.getExpenseId())
                .set("generation", GENERATION)
                .set("logicalKey", logicalKey(command))
                .set("lifecycleStatus", InsightLifecycleStatus.DELETED)
                .set("commandId", command.getCommandId())
                .set("sourceExpenseEventId", command.getSourceExpenseEventId())
                .set("deletedAt", now)
                .set("updatedAt", now)
                .unset("budgetSummaryMessage")
                .unset("spendingImprovements")
                .unset("savingSuggestions")
                .unset("budgetWarnings")
                .unset("severity")
                .unset("expenseCategory")
                .setOnInsert("createdAt", now);

        try {
            mongoTemplate.upsert(key, tombstone, AIInsight.class);
            log.info("Recorded AI insight tombstone commandId={} expenseId={}",
                    command.getCommandId(), command.getExpenseId());
        } catch (DuplicateKeyException duplicate) {
            AIInsight current = repository.findByOwnerSubjectAndExpenseIdAndGeneration(
                    command.getOwnerSubject(), command.getExpenseId(), GENERATION).orElse(null);
            if (current == null || current.getLifecycleStatus() == InsightLifecycleStatus.DELETED) {
                return;
            }
            throw duplicate;
        }
    }

    public void deleteForOwner(String ownerSubject, UUID expenseId) {
        AiCommand command = new AiCommand();
        command.setCommandId(UUID.randomUUID());
        command.setCommandType(com.sea.aiservice.command.AiCommandType.DELETE_BUDGET_INSIGHT);
        command.setSchemaVersion(1);
        command.setOwnerSubject(ownerSubject);
        command.setExpenseId(expenseId);
        command.setGeneration(GENERATION);
        command.setSourceExpenseEventId(UUID.randomUUID());
        command.setRequestedAt(Instant.now());
        processDelete(command);
    }

    private void handleExpectedDuplicate(AiCommand command) {
        AIInsight current = repository.findByOwnerSubjectAndExpenseIdAndGeneration(
                command.getOwnerSubject(), command.getExpenseId(), GENERATION).orElse(null);
        if (current == null) {
            throw new IllegalStateException("AI insight duplicate-key race could not be re-read");
        }
        if (current.getLifecycleStatus() == InsightLifecycleStatus.ACTIVE) {
            log.info("Duplicate AI generation already persisted for expenseId={}", command.getExpenseId());
            return;
        }
        if (current.getLifecycleStatus() == InsightLifecycleStatus.DELETED) {
            log.info("Stale AI generation blocked by tombstone for expenseId={}", command.getExpenseId());
            return;
        }
        throw new IllegalStateException("AI insight has unknown lifecycle state");
    }

    private void validate(GeneratedInsightResponse response) {
        if (response == null || blank(response.getBudgetSummaryMessage())) {
            throw new InvalidGeneratedInsightException("Gemini response is missing budget summary");
        }
        if (response.getSpendingImprovements() == null || response.getSpendingImprovements().size() != 3) {
            throw new InvalidGeneratedInsightException("Gemini response must contain exactly three improvements");
        }
        if (response.getSavingSuggestions() == null || response.getSavingSuggestions().size() != 3) {
            throw new InvalidGeneratedInsightException("Gemini response must contain exactly three saving suggestions");
        }
        validateText(response.getBudgetSummaryMessage(), "budget summary");
        response.getSpendingImprovements().forEach(item -> {
            if (item == null || blank(item.getArea()) || blank(item.getSuggestion())) {
                throw new InvalidGeneratedInsightException("Gemini improvement is incomplete");
            }
            validateText(item.getArea(), "improvement area");
            validateText(item.getSuggestion(), "improvement suggestion");
        });
        response.getSavingSuggestions().forEach(item -> {
            if (item == null || blank(item.getMethod()) || blank(item.getDescription())) {
                throw new InvalidGeneratedInsightException("Gemini saving suggestion is incomplete");
            }
            validateText(item.getMethod(), "saving method");
            validateText(item.getDescription(), "saving description");
        });
        if (response.getBudgetWarnings() == null || response.getBudgetWarnings().size() > 2) {
            throw new InvalidGeneratedInsightException("Gemini response contains too many warnings");
        }
        response.getBudgetWarnings().forEach(warning -> {
            if (blank(warning)) throw new InvalidGeneratedInsightException("Gemini warning is blank");
            validateText(warning, "budget warning");
        });
    }

    private void validateText(String value, String field) {
        if (value.length() > MAX_TEXT_LENGTH) {
            throw new InvalidGeneratedInsightException("Gemini " + field + " is too long");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private List<String> flattenImprovements(GeneratedInsightResponse response) {
        return response.getSpendingImprovements().stream()
                .map(item -> item.getArea().trim() + ": " + item.getSuggestion().trim())
                .toList();
    }

    private List<String> flattenSuggestions(GeneratedInsightResponse response) {
        return response.getSavingSuggestions().stream()
                .map(item -> item.getMethod().trim() + ": " + item.getDescription().trim())
                .toList();
    }

    private List<String> mergeWarnings(AiCommand command, GeneratedInsightResponse response) {
        if (!Boolean.TRUE.equals(command.getHasBudget())) return List.of();
        List<String> warnings = new ArrayList<>();
        if (command.getBudgetWarning() != null && !command.getBudgetWarning().isBlank()) {
            warnings.add(command.getBudgetWarning().trim());
        }
        if (response.getBudgetWarnings() != null) {
            response.getBudgetWarnings().stream()
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .filter(value -> warnings.stream().noneMatch(existing -> existing.equalsIgnoreCase(value)))
                    .forEach(warnings::add);
        }
        return warnings;
    }

    private SeverityLevel determineSeverity(AiCommand command) {
        if (!Boolean.TRUE.equals(command.getHasBudget())) return SeverityLevel.LOW;
        String status = command.getBudgetStatus() == null ? "" : command.getBudgetStatus().toUpperCase();
        return switch (status) {
            case "EXCEEDED", "NEAR_LIMIT" -> SeverityLevel.CRITICAL;
            case "CAUTION" -> SeverityLevel.HIGH;
            case "ON_TRACK" -> SeverityLevel.MEDIUM;
            default -> SeverityLevel.LOW;
        };
    }

    private String logicalKey(AiCommand command) {
        return command.getOwnerSubject() + ":" + command.getExpenseId() + ":" + GENERATION;
    }
}
