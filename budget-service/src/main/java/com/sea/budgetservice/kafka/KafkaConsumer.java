package com.sea.budgetservice.kafka;

import com.sea.budgetservice.service.ExpenseEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KafkaConsumer {

    private final ExpenseEventParser eventParser;
    private final ExpenseEventHandler eventHandler;

    @KafkaListener(
            topics = "${app.kafka.topics.expense-created:expense}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeExpenseEvent(byte[] eventBytes) {
        eventHandler.handle(eventParser.parse(eventBytes));
    }
}

