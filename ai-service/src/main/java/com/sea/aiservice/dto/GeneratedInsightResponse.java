package com.sea.aiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedInsightResponse {
    private String budgetSummaryMessage;
    private List<Recommendation> spendingImprovements;
    private List<SavingSuggestion> savingSuggestions;
    private List<String> budgetWarnings;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Recommendation {
        private String area;
        private String suggestion;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SavingSuggestion {
        private String method;
        private String description;
    }
}
