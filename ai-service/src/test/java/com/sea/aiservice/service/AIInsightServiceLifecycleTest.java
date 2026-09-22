package com.sea.aiservice.service;

import com.sea.aiservice.command.AiCommand;
import com.sea.aiservice.command.AiCommandType;
import com.sea.aiservice.dto.GeneratedInsightResponse;
import com.sea.aiservice.exception.InvalidGeneratedInsightException;
import com.sea.aiservice.model.InsightLifecycleStatus;
import com.sea.aiservice.repository.AIInsightRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AIInsightServiceLifecycleTest {

    @Mock InsightGenerationClient generationClient;
    @Mock AIInsightRepository repository;
    @Mock MongoTemplate mongoTemplate;

    @Test
    void doesNotCallProviderForDeletedTombstone() {
        UUID expenseId = UUID.randomUUID();
        var tombstone = com.sea.aiservice.model.AIInsight.builder()
                .ownerSubject("alice")
                .expenseId(expenseId)
                .generation(1)
                .lifecycleStatus(InsightLifecycleStatus.DELETED)
                .build();
        when(repository.findByOwnerSubjectAndExpenseIdAndGeneration("alice", expenseId, 1))
                .thenReturn(Optional.of(tombstone));

        new AIInsightService(generationClient, repository, mongoTemplate).processGenerate(command(expenseId));

        verifyNoInteractions(generationClient);
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void doesNotPersistFabricatedFallbackWhenProviderFails() {
        UUID expenseId = UUID.randomUUID();
        when(repository.findByOwnerSubjectAndExpenseIdAndGeneration("alice", expenseId, 1))
                .thenReturn(Optional.empty());
        when(generationClient.generate(any()))
                .thenThrow(new com.sea.aiservice.exception.RetryableAiProcessingException("temporary"));

        assertThrows(com.sea.aiservice.exception.RetryableAiProcessingException.class,
                () -> new AIInsightService(generationClient, repository, mongoTemplate)
                        .processGenerate(command(expenseId)));
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void duplicateGenerateAfterActiveInsightIsSuccessfulWithoutProviderCall() {
        UUID expenseId = UUID.randomUUID();
        var active = com.sea.aiservice.model.AIInsight.builder()
                .ownerSubject("alice").expenseId(expenseId).generation(1)
                .lifecycleStatus(InsightLifecycleStatus.ACTIVE).build();
        when(repository.findByOwnerSubjectAndExpenseIdAndGeneration("alice", expenseId, 1))
                .thenReturn(Optional.of(active));

        new AIInsightService(generationClient, repository, mongoTemplate).processGenerate(command(expenseId));

        verifyNoInteractions(generationClient);
    }

    @Test
    void activeDuplicateKeyRaceIsSuccessfulAfterReread() {
        UUID expenseId = UUID.randomUUID();
        AIInsightService service = new AIInsightService(generationClient, repository, mongoTemplate);
        when(repository.findByOwnerSubjectAndExpenseIdAndGeneration("alice", expenseId, 1))
                .thenReturn(Optional.empty(), Optional.of(active(expenseId)));
        when(generationClient.generate(any())).thenReturn(validResponse());
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(com.sea.aiservice.model.AIInsight.class)))
                .thenThrow(new DuplicateKeyException("expected concurrent generate race"));

        assertDoesNotThrow(() -> service.processGenerate(command(expenseId)));
    }

    @Test
    void deletedDuplicateKeyRaceTreatsGenerateAsStaleSuccess() {
        UUID expenseId = UUID.randomUUID();
        AIInsightService service = new AIInsightService(generationClient, repository, mongoTemplate);
        when(repository.findByOwnerSubjectAndExpenseIdAndGeneration("alice", expenseId, 1))
                .thenReturn(Optional.empty(), Optional.of(tombstone(expenseId)));
        when(generationClient.generate(any())).thenReturn(validResponse());
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(com.sea.aiservice.model.AIInsight.class)))
                .thenThrow(new DuplicateKeyException("expected generate-delete race"));

        assertDoesNotThrow(() -> service.processGenerate(command(expenseId)));
    }

    @Test
    void duplicateDeleteIsIdempotent() {
        UUID expenseId = UUID.randomUUID();
        AIInsightService service = new AIInsightService(generationClient, repository, mongoTemplate);

        assertDoesNotThrow(() -> service.processDelete(deleteCommand(expenseId)));
        assertDoesNotThrow(() -> service.processDelete(deleteCommand(expenseId)));
        verify(mongoTemplate, times(2)).upsert(any(Query.class), any(Update.class),
                eq(com.sea.aiservice.model.AIInsight.class));
    }

    @Test
    void malformedStructuredOutputIsRejectedWithoutPersistence() {
        UUID expenseId = UUID.randomUUID();
        when(repository.findByOwnerSubjectAndExpenseIdAndGeneration("alice", expenseId, 1))
                .thenReturn(Optional.empty());
        when(generationClient.generate(any())).thenReturn(GeneratedInsightResponse.builder()
                .budgetSummaryMessage(" ")
                .spendingImprovements(List.of())
                .savingSuggestions(List.of())
                .budgetWarnings(List.of())
                .build());

        assertThrows(InvalidGeneratedInsightException.class,
                () -> new AIInsightService(generationClient, repository, mongoTemplate)
                        .processGenerate(command(expenseId)));
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void wrongStructuredOutputCardinalityIsRejectedWithoutPersistence() {
        UUID expenseId = UUID.randomUUID();
        when(repository.findByOwnerSubjectAndExpenseIdAndGeneration("alice", expenseId, 1))
                .thenReturn(Optional.empty());
        when(generationClient.generate(any())).thenReturn(GeneratedInsightResponse.builder()
                .budgetSummaryMessage("Summary")
                .spendingImprovements(List.of(
                        new GeneratedInsightResponse.Recommendation("Food", "Plan meals")))
                .savingSuggestions(List.of(
                        new GeneratedInsightResponse.SavingSuggestion("Meal prep", "Cook at home"),
                        new GeneratedInsightResponse.SavingSuggestion("Compare", "Compare prices"),
                        new GeneratedInsightResponse.SavingSuggestion("Automate", "Automate savings")))
                .budgetWarnings(List.of())
                .build());

        assertThrows(InvalidGeneratedInsightException.class,
                () -> new AIInsightService(generationClient, repository, mongoTemplate)
                        .processGenerate(command(expenseId)));
        verifyNoInteractions(mongoTemplate);
    }

    private com.sea.aiservice.model.AIInsight active(UUID expenseId) {
        return com.sea.aiservice.model.AIInsight.builder()
                .ownerSubject("alice").expenseId(expenseId).generation(1)
                .lifecycleStatus(InsightLifecycleStatus.ACTIVE).build();
    }

    private com.sea.aiservice.model.AIInsight tombstone(UUID expenseId) {
        return com.sea.aiservice.model.AIInsight.builder()
                .ownerSubject("alice").expenseId(expenseId).generation(1)
                .lifecycleStatus(InsightLifecycleStatus.DELETED).build();
    }

    private GeneratedInsightResponse validResponse() {
        return GeneratedInsightResponse.builder()
                .budgetSummaryMessage("Summary")
                .spendingImprovements(List.of(
                        new GeneratedInsightResponse.Recommendation("Food", "Plan meals"),
                        new GeneratedInsightResponse.Recommendation("Bills", "Review bills"),
                        new GeneratedInsightResponse.Recommendation("Fees", "Avoid fees")))
                .savingSuggestions(List.of(
                        new GeneratedInsightResponse.SavingSuggestion("Meal prep", "Cook at home"),
                        new GeneratedInsightResponse.SavingSuggestion("Compare", "Compare prices"),
                        new GeneratedInsightResponse.SavingSuggestion("Automate", "Automate savings")))
                .budgetWarnings(List.of())
                .build();
    }

    private AiCommand command(UUID expenseId) {
        AiCommand command = new AiCommand();
        command.setCommandId(UUID.randomUUID());
        command.setCommandType(AiCommandType.GENERATE_BUDGET_INSIGHT);
        command.setSchemaVersion(1);
        command.setOwnerSubject("alice");
        command.setExpenseId(expenseId);
        command.setGeneration(1);
        command.setSourceExpenseEventId(UUID.randomUUID());
        command.setRequestedAt(Instant.now());
        command.setExpenseAmountCents(1234L);
        command.setExpenseCategory(com.sea.aiservice.model.ExpenseCategory.FOOD);
        command.setHasBudget(false);
        return command;
    }

    private AiCommand deleteCommand(UUID expenseId) {
        AiCommand command = command(expenseId);
        command.setCommandType(AiCommandType.DELETE_BUDGET_INSIGHT);
        command.setExpenseAmountCents(null);
        command.setExpenseCategory(null);
        command.setHasBudget(null);
        return command;
    }
}
