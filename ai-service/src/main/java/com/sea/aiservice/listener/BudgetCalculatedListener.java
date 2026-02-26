package com.sea.aiservice.listener;

import com.sea.aiservice.event.BudgetCalculatedEvent;
import com.sea.aiservice.model.AIInsight;
import com.sea.aiservice.repository.AIInsightRepository;
import com.sea.aiservice.service.AIInsightService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class BudgetCalculatedListener {

    private final AIInsightService aiInsightService;
    private final AIInsightRepository aiInsightRepository;

    @RabbitListener(queues = "${RABBITMQ_QUEUE_NAME:budget.queue}")
    public void processBudgetCalculatedEvent(BudgetCalculatedEvent event) {
        UUID expenseId = event.getExpenseId();

        log.info("Received BudgetCalculatedEvent: [ExpenseId={}, UserId={}, PercentageUsed={}%%]",
                expenseId, event.getUserId(), event.getPercentageUsed());

        if (aiInsightRepository.findByExpenseId(expenseId).isPresent()) {
            log.info("AI insight already exists for expenseId={}. Skipping.", expenseId);
            return;
        }

        try {
            AIInsight insight = aiInsightService.generateInsight(event);
            aiInsightRepository.save(insight);

            log.info("AI insight saved successfully: [ExpenseId={}, Severity={}]",
                    expenseId, insight.getSeverity());

        } catch (Exception e) {
            log.error("Failed to process BudgetCalculatedEvent for expenseId={}: {}",
                    expenseId, e.getMessage(), e);
            throw new RuntimeException("Failed to process budget event", e);
        }
    }
}
