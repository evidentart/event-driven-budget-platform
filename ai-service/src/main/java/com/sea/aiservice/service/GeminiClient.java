package com.sea.aiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sea.aiservice.exception.GeminiApiException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class GeminiClient {

    private final WebClient webClient;
    private final ObjectMapper mapper;

    @Value("${GEMINI_API_KEY:}")
    private String apiKey;

    @Value("${GEMINI_MODEL:gemini-2.5-flash}")
    private String model;

    @Value("${GEMINI_BASE_URL:https://generativelanguage.googleapis.com}")
    private String baseUrl;

    @Value("${GEMINI_TIMEOUT_SECONDS:30}")
    private int timeoutSeconds;

    @Value("${GEMINI_RATE_LIMIT_COOLDOWN_SECONDS:120}")
    private long rateLimitCooldownSeconds;

    private final AtomicLong rateLimitedUntilMs = new AtomicLong(0);

    public GeminiClient(WebClient.Builder builder, ObjectMapper mapper) {
        this.mapper = mapper;
        this.webClient = builder.build();
    }

    @CircuitBreaker(name = "gemini", fallbackMethod = "fallback")
    @Retry(name = "gemini")
    public Optional<String> generateText(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GEMINI_API_KEY is not set. Gemini disabled -> using fallback.");
            return Optional.empty();
        }

        long now = System.currentTimeMillis();
        long until = rateLimitedUntilMs.get();
        if (now < until) {
            return Optional.empty();
        }

        final String url = baseUrl + "/v1beta/models/" + model + ":generateContent";

        try {
            Map<String, Object> body = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(Map.of("text", prompt)))
                    )
            );

            String raw = webClient.post()
                    .uri(url)
                    .header("X-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            if (raw == null || raw.isBlank()) return Optional.empty();

            JsonNode root = mapper.readTree(raw);
            JsonNode textNode = root.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text");

            String text = textNode.isMissingNode() ? null : textNode.asText(null);
            if (text == null || text.isBlank()) return Optional.empty();

            return Optional.of(text);

        } catch (WebClientResponseException e) {
            int code = e.getStatusCode().value();

            if (code == 429) {
                log.warn("Gemini rate limited (429). model={}, url={}", model, url);

                long cooldownMs = Math.max(5, rateLimitCooldownSeconds) * 1000;
                rateLimitedUntilMs.set(System.currentTimeMillis() + cooldownMs);

                log.warn("Entering cooldown for {}s", rateLimitCooldownSeconds);
                return Optional.empty();
            }

            log.warn("Gemini error status={}. model={}, url={}", code, model, url);
            throw new GeminiApiException("Gemini call failed (model=" + model + ")", e);

        } catch (Exception e) {
            throw new GeminiApiException("Gemini call failed (model=" + model + ")", e);
        }
    }

    private Optional<String> fallback(String prompt, Throwable t) {
        log.warn("Gemini fallback used. Reason: {}", t.toString());
        return Optional.empty();
    }
}
