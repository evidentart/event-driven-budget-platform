package com.sea.aiservice.config;

import com.sea.aiservice.exception.NonRetryableAiCommandException;
import com.sea.aiservice.exception.RetryableAiProcessingException;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecovererWithConfirms;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RabbitRecoveryTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private MessageRecoverer messageRecoverer;

    @Mock
    private MethodInvocation invocation;

    private RabbitMqConfig config;

    @BeforeEach
    void setUp() {
        config = new RabbitMqConfig();
        ReflectionTestUtils.setField(config, "deadLetterExchangeName", "ai.dlx");
        ReflectionTestUtils.setField(config, "deadLetterRoutingKey", "ai.dead");
        lenient().when(invocation.getArguments()).thenReturn(new Object[]{null, message()});
    }

    @Test
    void retryableProcessingFailureGetsExactlyThreeNormalAttempts() throws Throwable {
        Advice advice = config.aiListenerRetryAdvice(messageRecoverer);
        MethodInterceptor interceptor = (MethodInterceptor) advice;
        AtomicInteger attempts = new AtomicInteger();
        when(invocation.proceed()).thenAnswer(ignored -> {
            attempts.incrementAndGet();
            throw new RetryableAiProcessingException("temporary");
        });

        interceptor.invoke(invocation);

        org.junit.jupiter.api.Assertions.assertEquals(3, attempts.get());
        verify(messageRecoverer).recover(any(), any(Throwable.class));
    }

    @Test
    void nonRetryableFailureGoesDirectlyToRecovery() throws Throwable {
        Advice advice = config.aiListenerRetryAdvice(messageRecoverer);
        MethodInterceptor interceptor = (MethodInterceptor) advice;
        when(invocation.proceed()).thenThrow(new NonRetryableAiCommandException("malformed"));

        interceptor.invoke(invocation);

        verify(invocation, times(1)).proceed();
        verify(messageRecoverer).recover(any(), any(Throwable.class));
    }

    @Test
    void successfulConfirmedRoutedDlqRecoveryCompletes() {
        RepublishMessageRecovererWithConfirms recoverer = recoverer();
        confirmDlq(true, false);

        assertDoesNotThrow(() -> recoverer.recover(message(), new RuntimeException("failed command")));
    }

    @Test
    void negativeDlqConfirmationFailsRecovery() {
        RepublishMessageRecovererWithConfirms recoverer = recoverer();
        confirmDlq(false, false);

        assertThrows(RuntimeException.class,
                () -> recoverer.recover(message(), new RuntimeException("failed command")));
    }

    @Test
    void returnedDlqMessageFailsRecovery() {
        RepublishMessageRecovererWithConfirms recoverer = recoverer();
        confirmDlq(true, true);

        assertThrows(RuntimeException.class,
                () -> recoverer.recover(message(), new RuntimeException("failed command")));
    }

    @Test
    void dlqConfirmationTimeoutFailsRecovery() {
        RepublishMessageRecovererWithConfirms recoverer = recoverer();
        recoverer.setConfirmTimeout(10L);
        doNothing().when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        assertThrows(RuntimeException.class,
                () -> recoverer.recover(message(), new RuntimeException("failed command")));
    }

    @Test
    void failedRecoveryIsPropagatedAndCannotSilentlyAcknowledgeOriginal() throws Throwable {
        Advice advice = config.aiListenerRetryAdvice(messageRecoverer);
        MethodInterceptor interceptor = (MethodInterceptor) advice;
        when(invocation.proceed()).thenThrow(new RetryableAiProcessingException("temporary"));
        doThrow(new IllegalStateException("DLQ unavailable"))
                .when(messageRecoverer).recover(any(), any(Throwable.class));

        assertThrows(IllegalStateException.class, () -> interceptor.invoke(invocation));
        verify(invocation, times(3)).proceed();
    }

    @Test
    void listenerFactoryUsesConfiguredAckConcurrencyPrefetchRequeueAndRetryAdvice() {
        Advice retryAdvice = config.aiListenerRetryAdvice(messageRecoverer);
        SimpleRabbitListenerContainerFactory factory = config.rabbitListenerContainerFactory(
                mock(org.springframework.amqp.rabbit.connection.ConnectionFactory.class),
                config.messageConverter(),
                retryAdvice);

        org.junit.jupiter.api.Assertions.assertEquals(
                AcknowledgeMode.AUTO, ReflectionTestUtils.getField(factory, "acknowledgeMode"));
        org.junit.jupiter.api.Assertions.assertEquals(
                1, ReflectionTestUtils.getField(factory, "prefetchCount"));
        org.junit.jupiter.api.Assertions.assertEquals(
                1, ReflectionTestUtils.getField(factory, "concurrentConsumers"));
        org.junit.jupiter.api.Assertions.assertEquals(
                1, ReflectionTestUtils.getField(factory, "maxConcurrentConsumers"));
        org.junit.jupiter.api.Assertions.assertEquals(
                true, ReflectionTestUtils.getField(factory, "defaultRequeueRejected"));
        org.junit.jupiter.api.Assertions.assertArrayEquals(
                new Advice[]{retryAdvice}, factory.getAdviceChain());
    }

    private RepublishMessageRecovererWithConfirms recoverer() {
        RepublishMessageRecovererWithConfirms recoverer =
                new RepublishMessageRecovererWithConfirms(
                        rabbitTemplate,
                        "ai.dlx",
                        "ai.dead",
                        CachingConnectionFactory.ConfirmType.CORRELATED);
        recoverer.setConfirmTimeout(100L);
        return recoverer;
    }

    private void confirmDlq(boolean ack, boolean returned) {
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            if (returned) {
                correlationData.setReturned(new ReturnedMessage(
                        invocation.getArgument(2), 312, "NO_ROUTE", "ai.dlx", "ai.dead"));
            }
            correlationData.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "negative"));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    private Message message() {
        return new Message("command".getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }
}
