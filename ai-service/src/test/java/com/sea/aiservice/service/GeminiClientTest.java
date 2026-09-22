package com.sea.aiservice.service;

import com.sea.aiservice.command.AiCommand;
import com.sea.aiservice.command.AiCommandType;
import com.sea.aiservice.dto.InsightGenerationRequest;
import com.sea.aiservice.exception.InvalidGeneratedInsightException;
import com.sea.aiservice.exception.NonRetryableAiCommandException;
import com.sea.aiservice.exception.RetryableAiProcessingException;
import com.sea.aiservice.repository.AIInsightRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeminiClientTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec uriSpec;

    @Mock
    private RestClient.RequestBodySpec bodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    @Mock
    private AIInsightRepository repository;

    @Mock
    private MongoTemplate mongoTemplate;

    private GeminiClient client;

    @BeforeEach
    void setUp() {
        client = new GeminiClient(restClient, new ObjectMapper(), "test-key", "test-model", "http://gemini");
        lenient().when(restClient.post()).thenReturn(uriSpec);
        lenient().when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        lenient().when(bodySpec.header(anyString(), anyString())).thenReturn(bodySpec);
        lenient().when(bodySpec.contentType(any())).thenReturn(bodySpec);
        lenient().when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
        lenient().when(bodySpec.retrieve()).thenReturn(responseSpec);
    }

    @Test
    void decodesValidStructuredJsonWithExactCardinality() {
        when(responseSpec.body(String.class)).thenReturn(validResponse());

        var response = client.generate(new InsightGenerationRequest(command()));

        org.junit.jupiter.api.Assertions.assertEquals("Summary", response.getBudgetSummaryMessage());
        org.junit.jupiter.api.Assertions.assertEquals(3, response.getSpendingImprovements().size());
        org.junit.jupiter.api.Assertions.assertEquals(3, response.getSavingSuggestions().size());
    }

    @Test
    void malformedJsonIsTerminal() {
        when(responseSpec.body(String.class)).thenReturn("not-json");

        assertThrows(InvalidGeneratedInsightException.class,
                () -> client.generate(new InsightGenerationRequest(command())));
    }

    @Test
    void blankRawResponseIsRejected() {
        when(responseSpec.body(String.class)).thenReturn("   ");

        assertThrows(InvalidGeneratedInsightException.class,
                () -> client.generate(new InsightGenerationRequest(command())));
    }

    @Test
    void wrongImprovementCardinalityIsRejectedBeforePersistence() {
        assertRejectedByService("{\"budgetSummaryMessage\":\"Summary\","
                + "\"spendingImprovements\":[{\"area\":\"Food\",\"suggestion\":\"Plan meals\"}],"
                + "\"savingSuggestions\":[{\"method\":\"Meal prep\",\"description\":\"Cook at home\"},"
                + "{\"method\":\"Compare\",\"description\":\"Compare prices\"},"
                + "{\"method\":\"Automate\",\"description\":\"Automate savings\"}],"
                + "\"budgetWarnings\":[]}");
    }

    @Test
    void wrongSavingSuggestionCardinalityIsRejectedBeforePersistence() {
        assertRejectedByService("{\"budgetSummaryMessage\":\"Summary\","
                + "\"spendingImprovements\":[{\"area\":\"Food\",\"suggestion\":\"Plan meals\"},"
                + "{\"area\":\"Bills\",\"suggestion\":\"Review bills\"},"
                + "{\"area\":\"Fees\",\"suggestion\":\"Avoid fees\"}],"
                + "\"savingSuggestions\":[{\"method\":\"Meal prep\",\"description\":\"Cook at home\"}],"
                + "\"budgetWarnings\":[]}");
    }

    @Test
    void blankNestedRequiredFieldIsRejectedBeforePersistence() {
        assertRejectedByService("{\"budgetSummaryMessage\":\"Summary\","
                + "\"spendingImprovements\":[{\"area\":\"\",\"suggestion\":\"Plan meals\"},"
                + "{\"area\":\"Bills\",\"suggestion\":\"Review bills\"},"
                + "{\"area\":\"Fees\",\"suggestion\":\"Avoid fees\"}],"
                + "\"savingSuggestions\":[{\"method\":\"Meal prep\",\"description\":\"Cook at home\"},"
                + "{\"method\":\"Compare\",\"description\":\"Compare prices\"},"
                + "{\"method\":\"Automate\",\"description\":\"Automate savings\"}],"
                + "\"budgetWarnings\":[]}");
    }

    @Test
    void ordinaryFourHundredResponseIsNonRetryable() {
        when(responseSpec.body(String.class)).thenThrow(HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "bad request", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));

        assertThrows(NonRetryableAiCommandException.class,
                () -> client.generate(new InsightGenerationRequest(command())));
        verify(responseSpec, times(1)).body(String.class);
    }

    @Test
    void rateLimitAndServerErrorsAreRetryableAfterBoundedAttempts() {
        when(responseSpec.body(String.class)).thenThrow(
                HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "rate limited",
                        HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8),
                HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "server error",
                        HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));

        assertThrows(RetryableAiProcessingException.class,
                () -> client.generate(new InsightGenerationRequest(command())));
        verify(responseSpec, times(2)).body(String.class);
    }

    @Test
    void timeoutAndNetworkErrorsAreRetryableAfterBoundedAttempts() {
        when(responseSpec.body(String.class)).thenThrow(
                new ResourceAccessException("timeout"), new ResourceAccessException("connection down"));

        assertThrows(RetryableAiProcessingException.class,
                () -> client.generate(new InsightGenerationRequest(command())));
        verify(responseSpec, times(2)).body(String.class);
    }

    @Test
    void missingApiKeyIsTerminalWithoutCallingGemini() {
        GeminiClient missingKey = new GeminiClient(restClient, new ObjectMapper(), "", "test-model", "http://gemini");

        assertThrows(NonRetryableAiCommandException.class,
                () -> missingKey.generate(new InsightGenerationRequest(command())));
        verifyNoInteractions(restClient);
    }

    private AiCommand command() {
        AiCommand command = new AiCommand();
        command.setCommandId(UUID.randomUUID());
        command.setCommandType(AiCommandType.GENERATE_BUDGET_INSIGHT);
        command.setSchemaVersion(1);
        command.setOwnerSubject("alice");
        command.setExpenseId(UUID.randomUUID());
        command.setGeneration(1);
        command.setSourceExpenseEventId(UUID.randomUUID());
        command.setRequestedAt(Instant.now());
        command.setExpenseAmountCents(1234L);
        command.setHasBudget(false);
        return command;
    }

    private void assertRejectedByService(String structuredJson) {
        when(responseSpec.body(String.class)).thenReturn(responseWithStructuredJson(structuredJson));

        AIInsightService service = new AIInsightService(client, repository, mongoTemplate);
        assertThrows(InvalidGeneratedInsightException.class,
                () -> service.processGenerate(command()));
        verifyNoInteractions(mongoTemplate);
    }

    private String responseWithStructuredJson(String structuredJson) {
        String escaped = structuredJson.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\""
                + escaped + "\"}]}}]}";
    }

    private String validResponse() {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\""
                + "{\\\"budgetSummaryMessage\\\":\\\"Summary\\\","
                + "\\\"spendingImprovements\\\":["
                + "{\\\"area\\\":\\\"Food\\\",\\\"suggestion\\\":\\\"Plan meals\\\"},"
                + "{\\\"area\\\":\\\"Bills\\\",\\\"suggestion\\\":\\\"Review bills\\\"},"
                + "{\\\"area\\\":\\\"Fees\\\",\\\"suggestion\\\":\\\"Avoid fees\\\"}],"
                + "\\\"savingSuggestions\\\":["
                + "{\\\"method\\\":\\\"Meal prep\\\",\\\"description\\\":\\\"Cook at home\\\"},"
                + "{\\\"method\\\":\\\"Compare\\\",\\\"description\\\":\\\"Compare prices\\\"},"
                + "{\\\"method\\\":\\\"Automate\\\",\\\"description\\\":\\\"Automate savings\\\"}],"
                + "\\\"budgetWarnings\\\":[]}\"} ]}}]}";
    }
}
