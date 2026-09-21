package com.sea.aiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sea.aiservice.event.BudgetCalculatedEvent;
import com.sea.aiservice.model.AIInsight;
import com.sea.aiservice.model.SeverityLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class AIInsightService {

    private final GeminiClient geminiClient;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public AIInsight generateInsight(BudgetCalculatedEvent event) {
        try {
            String prompt = createPrompt(event);

            String aiText = geminiClient.generateText(prompt).orElse(null);
            if (aiText == null) {
                log.warn("Gemini unavailable for expenseId={}, using default insight", event.getExpenseId());
                return createDefaultInsight(event);
            }

            return parseAIResponse(event, aiText);

        } catch (Exception e) {
            log.error("Failed to generate insight for expenseId={}: {}", event.getExpenseId(), e.getMessage(), e);
            return createDefaultInsight(event);
        }
    }

    private String createPrompt(BudgetCalculatedEvent event) {
        if (!event.isHasBudget()) {
            return String.format("""
                Analyze the user's latest expense and respond with VALID JSON ONLY (no markdown, no extra text).

                Return EXACTLY this JSON shape:
                {
                  "budgetSummaryMessage": "text",
                  "spendingImprovements": [
                    { "area": "Area name", "suggestion": "2 sentences" },
                    { "area": "Area name", "suggestion": "2 sentences" },
                    { "area": "Area name", "suggestion": "2 sentences" }
                  ],
                  "savingSuggestions": [
                    { "method": "Saving method", "description": "2 sentences" },
                    { "method": "Saving method", "description": "2 sentences" },
                    { "method": "Saving method", "description": "2 sentences" }
                  ],
                  "budgetWarnings": []
                }

                User's Current Situation:
                - Latest Expense: $%.2f
                - Category: %s
                - Budget Status: NO BUDGET SET for this month

                STRICT RULES:
                - budgetSummaryMessage MUST be exactly 3 sentences.
                  * Sentence 1: acknowledge the category/expense.
                  * Sentence 2: explain what this kind of spending typically affects (without judging).
                  * Sentence 3: suggest setting a monthly budget to unlock deeper alerts.
                - spendingImprovements MUST have exactly 3 items.
                  * Each "suggestion" MUST be exactly 2 sentences.
                  * Each item must use a DIFFERENT tactic (no repeating meal prep / compare prices / track expenses).
                  * Keep it specific to the category.
                - savingSuggestions MUST have exactly 3 items.
                  * Each "description" MUST be exactly 2 sentences.
                  * Must not overlap with spendingImprovements tactics.
                - budgetWarnings MUST be an empty array [].
                - Do NOT say over/under budget (because no budget exists).
                - Do NOT invent numbers or claim trends.

                Output JSON only.
                """,
                    event.getExpenseAmount(),
                    event.getExpenseCategory()
            );
        }

        return String.format("""
            Analyze the user's latest expense and budget situation and respond with VALID JSON ONLY (no markdown, no extra text).

            Return EXACTLY this JSON shape:
            {
              "budgetSummaryMessage": "text",
              "spendingImprovements": [
                { "area": "Area name", "suggestion": "2-3 sentences" },
                { "area": "Area name", "suggestion": "2-3 sentences" },
                { "area": "Area name", "suggestion": "2-3 sentences" }
              ],
              "savingSuggestions": [
                { "method": "Saving method", "description": "2 sentences" },
                { "method": "Saving method", "description": "2 sentences" },
                { "method": "Saving method", "description": "2 sentences" }
              ],
              "budgetWarnings": [
                "warning text"
              ]
            }

            User's Current Situation:
            - Latest Expense: $%.2f
            - Category: %s
            - Total Budget: $%.2f
            - Total Spent: $%.2f
            - Remaining Budget: $%.2f
            - Percentage Used: %.1f%%
            - Budget Status: %s

            STRICT RULES:
            - budgetSummaryMessage MUST be exactly 3 sentences.
              * Mention category + percentageUsed and/or remainingBudget.
              * Be encouraging but honest.
              * End with one clear next action for THIS month.
            - spendingImprovements MUST have exactly 3 items.
              * Each "suggestion" MUST be 2-3 sentences.
              * Each item must use a DIFFERENT tactic (no duplicates).
              * Must be specific to the category and the budget status.
            - savingSuggestions MUST have exactly 3 items.
              * Each "description" MUST be exactly 2 sentences.
              * Must not repeat any spendingImprovement tactic.
            - budgetWarnings:
              * If budget is healthy (remainingBudget > 0 AND percentageUsed < 75) → return [].
              * Otherwise return 1-2 warnings, each 2 sentences (risk + what to watch next).
            - Do NOT invent numbers beyond what is provided.

            Output JSON only.
            """,
                event.getExpenseAmount(),
                event.getExpenseCategory(),
                event.getTotalBudget(),
                event.getUsedBudget(),
                event.getRemainingBudget(),
                event.getPercentageUsed(),
                resolveBudgetStatusLabel(event)
        );
    }

    private AIInsight parseAIResponse(BudgetCalculatedEvent event, String aiText) {
        try {
            String cleaned = aiText
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            JsonNode json = MAPPER.readTree(cleaned);

            String budgetSummary = json.path("budgetSummaryMessage").asText("");

            List<String> improvements = extractKeyValueList(
                    json.path("spendingImprovements"), "area", "suggestion");

            List<String> suggestions = extractKeyValueList(
                    json.path("savingSuggestions"), "method", "description");

            List<String> warnings = extractStringList(json.path("budgetWarnings"));

            if (!event.isHasBudget()) {
                warnings = List.of();
            }
            warnings = mergeWithEventWarning(event, warnings);

            return AIInsight.builder()
                    .ownerSubject(event.getOwnerSubject())
                    .expenseId(event.getExpenseId())
                    .expenseCategory(event.getExpenseCategory())
                    .severity(determineSeverity(event))
                    .budgetSummaryMessage(budgetSummary.isBlank() ? "Spending analyzed." : budgetSummary)
                    .spendingImprovements(defaultIfEmpty(improvements, "Track your spending regularly."))
                    .savingSuggestions(defaultIfEmpty(suggestions, "Look for small savings opportunities."))
                    .budgetWarnings(warnings)
                    .createdAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse AI JSON for expenseId={}: {}", event.getExpenseId(), e.getMessage());
            return createDefaultInsight(event);
        }
    }

    private AIInsight createDefaultInsight(BudgetCalculatedEvent event) {
        if (!event.isHasBudget()) {
            return AIInsight.builder()
                    .ownerSubject(event.getOwnerSubject())
                    .expenseId(event.getExpenseId())
                    .expenseCategory(event.getExpenseCategory())
                    .severity(SeverityLevel.LOW)
                    .budgetSummaryMessage("You logged an expense. Set a monthly budget to unlock deeper recommendations and alerts.")
                    .spendingImprovements(List.of(
                            "Review your " + event.getExpenseCategory() + " spending weekly.",
                            "Group expenses into categories to see where money goes.",
                            "Set a simple monthly budget target to stay on track."
                    ))
                    .savingSuggestions(List.of(
                            "Try a 24-hour rule before non-essential purchases.",
                            "Set an automatic weekly transfer to savings.",
                            "Look for cheaper alternatives in your most frequent categories."
                    ))
                    .budgetWarnings(List.of())
                    .createdAt(LocalDateTime.now())
                    .build();
        }

        List<String> warnings = new ArrayList<>();
        String eventWarning = safeTrim(event.getBudgetWarning());
        if (!eventWarning.isEmpty()) {
            warnings.add(eventWarning);
        } else if (event.getRemainingBudget() < 0) {
            warnings.add("Your budget has been exceeded.");
        } else if (event.getPercentageUsed() >= 90) {
            warnings.add("You're at " + String.format("%.0f", event.getPercentageUsed()) + "% of your budget.");
        } else if (event.getPercentageUsed() >= 75) {
            warnings.add("You're approaching your budget limit.");
        }

        return AIInsight.builder()
                .ownerSubject(event.getOwnerSubject())
                .expenseId(event.getExpenseId())
                .expenseCategory(event.getExpenseCategory())
                .severity(determineSeverity(event))
                .budgetSummaryMessage("You have used " + String.format("%.1f", event.getPercentageUsed()) + "% of your monthly budget.")
                .spendingImprovements(List.of(
                        "Review your " + event.getExpenseCategory() + " expenses regularly.",
                        "Set spending limits for different categories.",
                        "Track daily expenses to stay aware of your spending."
                ))
                .savingSuggestions(List.of(
                        "Compare prices before making purchases.",
                        "Use budgeting apps to monitor spending patterns.",
                        "Set aside savings first (pay yourself first)."
                ))
                .budgetWarnings(warnings)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private SeverityLevel determineSeverity(BudgetCalculatedEvent event) {
        if (!event.isHasBudget()) return SeverityLevel.LOW;

        String budgetStatus = safeTrim(event.getBudgetStatus()).toUpperCase(Locale.ROOT);
        switch (budgetStatus) {
            case "EXCEEDED", "NEAR_LIMIT" -> {
                return SeverityLevel.CRITICAL;
            }
            case "CAUTION" -> {
                return SeverityLevel.HIGH;
            }
            case "ON_TRACK" -> {
                return SeverityLevel.MEDIUM;
            }
            case "HEALTHY" -> {
                return SeverityLevel.LOW;
            }
            default -> {
                // Fall through to percentage-based mapping when producer doesn't set status.
            }
        }

        double pct = event.getPercentageUsed();
        if (pct >= 90) return SeverityLevel.CRITICAL;
        if (pct >= 75) return SeverityLevel.HIGH;
        if (pct >= 50) return SeverityLevel.MEDIUM;
        return SeverityLevel.LOW;
    }

    private List<String> mergeWithEventWarning(BudgetCalculatedEvent event, List<String> warnings) {
        if (!event.isHasBudget()) return List.of();

        String eventWarning = safeTrim(event.getBudgetWarning());
        if (eventWarning.isEmpty()) return warnings;

        List<String> merged = new ArrayList<>();
        merged.add(eventWarning);
        if (warnings != null) {
            warnings.stream()
                    .filter(w -> !safeTrim(w).isEmpty())
                    .filter(w -> !eventWarning.equalsIgnoreCase(safeTrim(w)))
                    .forEach(merged::add);
        }
        return merged;
    }

    private String resolveBudgetStatusLabel(BudgetCalculatedEvent event) {
        String status = safeTrim(event.getBudgetStatus());
        if (!status.isEmpty()) return status;
        return event.getRemainingBudget() < 0 ? "EXCEEDED" : "Within budget";
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private List<String> extractKeyValueList(JsonNode arrayNode, String key1, String key2) {
        List<String> list = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            arrayNode.forEach(item -> {
                String k = item.path(key1).asText("");
                String v = item.path(key2).asText("");
                if (!k.isEmpty() && !v.isEmpty()) list.add(k + ": " + v);
            });
        }
        return list;
    }

    private List<String> extractStringList(JsonNode arrayNode) {
        List<String> list = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            arrayNode.forEach(item -> {
                String text = item.asText("");
                if (!text.isEmpty()) list.add(text);
            });
        }
        return list;
    }

    private List<String> defaultIfEmpty(List<String> list, String defaultMessage) {
        return (list == null || list.isEmpty()) ? Collections.singletonList(defaultMessage) : list;
    }
}
