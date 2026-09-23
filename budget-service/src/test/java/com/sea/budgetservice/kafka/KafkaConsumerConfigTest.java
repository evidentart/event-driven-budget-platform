package com.sea.budgetservice.kafka;

import com.sea.budgetservice.config.KafkaConsumerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class KafkaConsumerConfigTest {

    @Test
    void classifiesMalformedAndIllegalEventsAsNonRetryable() {
        KafkaConsumerConfig config = new KafkaConsumerConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");

        DefaultErrorHandler handler = (DefaultErrorHandler) config.kafkaErrorHandler(
                mock(KafkaTemplate.class));

        assertEquals(Boolean.FALSE, handler.removeClassification(MalformedExpenseEventException.class));
        assertEquals(Boolean.FALSE, handler.removeClassification(IllegalArgumentException.class));
        assertEquals(Boolean.FALSE, handler.removeClassification(IllegalStateException.class));
    }
}
