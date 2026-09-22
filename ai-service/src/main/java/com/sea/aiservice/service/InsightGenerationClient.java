package com.sea.aiservice.service;

import com.sea.aiservice.dto.GeneratedInsightResponse;
import com.sea.aiservice.dto.InsightGenerationRequest;

public interface InsightGenerationClient {
    GeneratedInsightResponse generate(InsightGenerationRequest request);
}
