package com.sea.aiservice.dto;

import com.sea.aiservice.command.AiCommand;

public record InsightGenerationRequest(AiCommand command) {
}
