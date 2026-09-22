package com.sea.aiservice.config;

import com.sea.aiservice.exception.RetryableAiProcessingException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecovererWithConfirms;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.aopalliance.aop.Advice;

import java.time.Duration;

@Configuration
public class RabbitMqConfig {

    @Value("${app.rabbitmq.exchange:ai.commands.v1.exchange}")
    private String exchangeName;

    @Value("${app.rabbitmq.queue:ai.commands.v1.queue}")
    private String queueName;

    @Value("${app.rabbitmq.generate-routing-key:ai.insight.generate.v1}")
    private String generateRoutingKey;

    @Value("${app.rabbitmq.delete-routing-key:ai.insight.delete.v1}")
    private String deleteRoutingKey;

    @Value("${app.rabbitmq.dlx:ai.commands.v1.dlx}")
    private String deadLetterExchangeName;

    @Value("${app.rabbitmq.dlq:ai.commands.v1.dlq}")
    private String deadLetterQueueName;

    @Value("${app.rabbitmq.dlq-routing-key:ai.command.dead}")
    private String deadLetterRoutingKey;

    @Bean
    public DirectExchange aiCommandExchange() {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public Queue aiCommandQueue() {
        return new Queue(queueName, true);
    }

    @Bean
    public DirectExchange aiDeadLetterExchange() {
        return new DirectExchange(deadLetterExchangeName, true, false);
    }

    @Bean
    public Queue aiDeadLetterQueue() {
        return new Queue(deadLetterQueueName, true);
    }

    @Bean
    public Binding generateBinding(Queue aiCommandQueue, DirectExchange aiCommandExchange) {
        return BindingBuilder.bind(aiCommandQueue).to(aiCommandExchange).with(generateRoutingKey);
    }

    @Bean
    public Binding deleteBinding(Queue aiCommandQueue, DirectExchange aiCommandExchange) {
        return BindingBuilder.bind(aiCommandQueue).to(aiCommandExchange).with(deleteRoutingKey);
    }

    @Bean
    public Binding deadLetterBinding(Queue aiDeadLetterQueue, DirectExchange aiDeadLetterExchange) {
        return BindingBuilder.bind(aiDeadLetterQueue).to(aiDeadLetterExchange).with(deadLetterRoutingKey);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setMandatory(true);
        return template;
    }

    @Bean
    public MessageRecoverer aiMessageRecoverer(RabbitTemplate rabbitTemplate) {
        RepublishMessageRecovererWithConfirms recoverer =
                new RepublishMessageRecovererWithConfirms(
                        rabbitTemplate,
                        deadLetterExchangeName,
                        deadLetterRoutingKey,
                        CachingConnectionFactory.ConfirmType.CORRELATED);
        recoverer.setConfirmTimeout(10_000L);
        return recoverer;
    }

    @Bean
    public Advice aiListenerRetryAdvice(MessageRecoverer aiMessageRecoverer) {
        RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxRetries(2)
                .delay(Duration.ofSeconds(1))
                .multiplier(2.0)
                .maxDelay(Duration.ofSeconds(5))
                .includes(RetryableAiProcessingException.class,
                        org.springframework.dao.DataAccessException.class)
                .build();

        return RetryInterceptorBuilder.stateless()
                .retryPolicy(retryPolicy)
                .recoverer(aiMessageRecoverer)
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            Advice aiListenerRetryAdvice
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setDefaultRequeueRejected(true);
        factory.setPrefetchCount(1);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        factory.setAdviceChain(aiListenerRetryAdvice);
        return factory;
    }
}
