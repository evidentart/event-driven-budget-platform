package com.sea.aiservice.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.sea.aiservice.command.AiCommand;
import com.sea.aiservice.dto.GeneratedInsightResponse;
import com.sea.aiservice.dto.InsightGenerationRequest;
import com.sea.aiservice.exception.InvalidGeneratedInsightException;
import com.sea.aiservice.exception.NonRetryableAiCommandException;
import com.sea.aiservice.exception.RetryableAiProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GeminiClient implements InsightGenerationClient {

    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    @Autowired
    public GeminiClient(
            ObjectMapper mapper,
            @Value("${GEMINI_API_KEY:}") String apiKey,
            @Value("${GEMINI_MODEL:gemini-2.5-flash}") String model,
            @Value("${GEMINI_BASE_URL:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${GEMINI_TIMEOUT_SECONDS:10}") int timeoutSeconds
    ) {
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    GeminiClient(RestClient restClient, ObjectMapper mapper, String apiKey, String model, String baseUrl) {
        this.restClient = restClient;
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
    }

    @Override
    public GeneratedInsightResponse generate(InsightGenerationRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new NonRetryableAiCommandException("GEMINI_API_KEY is not configured");
        }

        String prompt = buildPrompt(request.command());
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return decode(callGemini(prompt));
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if (isRetryableStatus(status) && attempt < 2) {
                    sleepBeforeRetry();
                    lastFailure = new RetryableAiProcessingException("Transient Gemini response status=" + status, e);
                    continue;
                }
                if (isRetryableStatus(status)) {
                    throw new RetryableAiProcessingException("Gemini request failed after retries", e);
                }
                throw new NonRetryableAiCommandException("Gemini rejected the request with status=" + status, e);
            } catch (ResourceAccessException e) {
                if (attempt < 2) {
                    sleepBeforeRetry();
                    lastFailure = new RetryableAiProcessingException("Transient Gemini transport failure", e);
                    continue;
                }
                throw new RetryableAiProcessingException("Gemini request timed out or failed", e);
            }
        }
        throw lastFailure == null
                ? new RetryableAiProcessingException("Gemini request failed") : lastFailure;
    }

    private String callGemini(String prompt) {
        String url = baseUrl + "/v1beta/models/" + model + ":generateContent";
        Map<String, Object> recommendationSchema = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "area", Map.of("type", "STRING"),
                        "suggestion", Map.of("type", "STRING")
                ),
                "required", List.of("area", "suggestion")
        );
        Map<String, Object> savingSuggestionSchema = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "method", Map.of("type", "STRING"),
                        "description", Map.of("type", "STRING")
                ),
                "required", List.of("method", "description")
        );
        Map<String, Object> responseSchema = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "budgetSummaryMessage", Map.of("type", "STRING"),
                        "spendingImprovements", Map.of("type", "ARRAY", "items", recommendationSchema),
                        "savingSuggestions", Map.of("type", "ARRAY", "items", savingSuggestionSchema),
                        "budgetWarnings", Map.of("type", "ARRAY", "items", Map.of("type", "STRING"))
                ),
                "required", List.of("budgetSummaryMessage", "spendingImprovements", "savingSuggestions", "budgetWarnings")
        );
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", responseSchema
                )
        );

        return restClient.post()
                .uri(url)
                .header("X-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
    }

    private GeneratedInsightResponse decode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidGeneratedInsightException("Gemini response is empty");
        }
        try {
            JsonNode root = mapper.readTree(raw);
            JsonNode text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (!text.isTextual() || text.asText().isBlank()) {
                throw new InvalidGeneratedInsightException("Gemini response has no structured candidate");
            }
            return mapper.readValue(text.asText(), GeneratedInsightResponse.class);
        } catch (InvalidGeneratedInsightException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidGeneratedInsightException("Gemini response is not valid structured JSON");
        }
    }

    private String buildPrompt(AiCommand command) {
        String budget = Boolean.TRUE.equals(command.getHasBudget())
                ? "Budget status: " + command.getBudgetStatus() + ", total budget cents: "
                + command.getTotalBudgetCents() + ", used budget cents: " + command.getUsedBudgetCents()
                + ", remaining budget cents: " + command.getRemainingBudgetCents()
                + ", percentage used: " + command.getPercentageUsed()
                : "No monthly budget is set for this period.";
        return "Analyze this financial situation and return JSON only. "
                + "Return exactly three spendingImprovements and exactly three savingSuggestions. "
                + "Each improvement must contain area and suggestion. "
                + "Each saving suggestion must contain method and description. "
                + "budgetWarnings must be an array with at most two strings. "
                + "Do not invent financial numbers. Expense cents: " + command.getExpenseAmountCents()
                + ", category: " + command.getExpenseCategory() + ". " + budget;
    }

    private boolean isRetryableStatus(int status) {
        return status == 429 || status >= 500;
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(500L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RetryableAiProcessingException("Gemini retry interrupted", e);
        }
    }
}
