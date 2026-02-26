package com.sea.aiservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Value("${RABBITMQ_QUEUE_NAME:budget.queue}")
    private String queue;

    @Value("${RABBITMQ_EXCHANGE_NAME:expense.exchange}")
    private String exchange;

    @Value("${RABBITMQ_ROUTING_KEY:budget.tracking}")
    private String routingKey;

    @Value("${RABBITMQ_PREFETCH:3}")
    private int prefetchCount;

    @Value("${RABBITMQ_CONCURRENCY:2}")
    private int concurrentConsumers;

    @Value("${RABBITMQ_MAX_CONCURRENCY:5}")
    private int maxConcurrentConsumers;

    @Bean
    public Queue budgetQueue() {
        return new Queue(queue, true);
    }

    @Bean
    public DirectExchange budgetExchange() {
        return new DirectExchange(exchange);
    }

    @Bean
    public Binding budgetBinding(Queue budgetQueue, DirectExchange budgetExchange) {
        return BindingBuilder.bind(budgetQueue).to(budgetExchange).with(routingKey);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setPrefetchCount(prefetchCount);
        factory.setConcurrentConsumers(concurrentConsumers);
        factory.setMaxConcurrentConsumers(maxConcurrentConsumers);
        return factory;
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter("com.sea");
    }
}
