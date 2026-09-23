package com.sea.aiservice.listener;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.sea.aiservice.command.AiCommand;
import com.sea.aiservice.command.AiCommandType;
import com.sea.aiservice.exception.NonRetryableAiCommandException;
import com.sea.aiservice.service.AIInsightService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class AiCommandListener {

    private final ObjectMapper objectMapper;
    private final AIInsightService insightService;

    @RabbitListener(queues = "${app.rabbitmq.queue:ai.commands.v1.queue}",
            containerFactory = "rabbitListenerContainerFactory")
    public void process(Message message) {
        AiCommand command = parse(message);
        validate(command);

        if (command.getCommandType() == AiCommandType.GENERATE_BUDGET_INSIGHT) {
            insightService.processGenerate(command);
        } else if (command.getCommandType() == AiCommandType.DELETE_BUDGET_INSIGHT) {
            insightService.processDelete(command);
        } else {
            throw new NonRetryableAiCommandException("Unsupported AI command type");
        }
    }

    private AiCommand parse(Message message) {
        try {
            return objectMapper.readValue(message.getBody(), AiCommand.class);
        } catch (JacksonException e) {
            throw new NonRetryableAiCommandException("AI command payload is malformed", e);
        } catch (RuntimeException e) {
            throw new NonRetryableAiCommandException("AI command payload is malformed", e);
        }
    }

    private void validate(AiCommand command) {
        if (command == null || command.getCommandId() == null || command.getCommandType() == null) {
            throw new NonRetryableAiCommandException("AI command is missing identity or type");
        }
        if (command.getSchemaVersion() != 1) {
            throw new NonRetryableAiCommandException("Unsupported AI command schema version");
        }
        if (command.getOwnerSubject() == null || command.getOwnerSubject().isBlank()) {
            throw new NonRetryableAiCommandException("AI command is missing ownerSubject");
        }
        if (command.getExpenseId() == null || command.getSourceExpenseEventId() == null
                || command.getRequestedAt() == null || command.getGeneration() != 1) {
            throw new NonRetryableAiCommandException("AI command is missing required identity fields");
        }
        if (command.getCommandType() == AiCommandType.GENERATE_BUDGET_INSIGHT) {
            if (command.getExpenseAmountCents() == null || command.getExpenseAmountCents() <= 0
                    || command.getExpenseCategory() == null || command.getHasBudget() == null) {
                throw new NonRetryableAiCommandException("Generate command contains invalid financial data");
            }
        }
    }
}
