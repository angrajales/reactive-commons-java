package org.reactivecommons.async.rabbit.listeners;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivecommons.async.api.handlers.QueryHandler;
import org.reactivecommons.async.api.handlers.registered.RegisteredQueryHandler;
import org.reactivecommons.async.helpers.SampleClass;
import org.reactivecommons.async.helpers.TestStubs;
import org.reactivecommons.async.commons.DiscardNotifier;
import org.reactivecommons.async.rabbit.HandlerResolver;
import org.reactivecommons.async.commons.communications.Message;
import org.reactivecommons.async.rabbit.communications.ReactiveMessageListener;
import org.reactivecommons.async.rabbit.communications.ReactiveMessageSender;
import org.reactivecommons.async.rabbit.communications.TopologyCreator;
import org.reactivecommons.async.commons.converters.MessageConverter;
import org.reactivecommons.async.commons.converters.json.DefaultObjectMapperSupplier;
import org.reactivecommons.async.rabbit.converters.json.JacksonMessageConverter;
import org.reactivecommons.async.commons.ext.CustomReporter;
import org.reactivecommons.async.rabbit.listeners.ApplicationQueryListener;
import org.reactivecommons.async.rabbit.listeners.GenericMessageListener;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.Receiver;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.*;
import static reactor.core.publisher.Mono.*;

@ExtendWith(MockitoExtension.class)
class ApplicationQueryListenerTest {
    private final MessageConverter messageConverter =
            new JacksonMessageConverter(new DefaultObjectMapperSupplier().get());
    @Mock
    private Receiver receiver;
    @Mock
    private ReactiveMessageSender sender;
    @Mock
    private DiscardNotifier discardNotifier;
    @Mock
    private TopologyCreator topologyCreator;

    @Mock
    private CustomReporter errorReporter;

    @Mock
    private ReactiveMessageListener reactiveMessageListener;
    private GenericMessageListener genericMessageListener;

    @SuppressWarnings("rawtypes")
    @BeforeEach
    public void setUp() {
//        when(errorReporter.reportError(any(Throwable.class), any(Message.class), any(Object.class))).thenReturn(Mono.empty());
        when(reactiveMessageListener.getReceiver()).thenReturn(receiver);
        Optional<Integer> maxLengthBytes = Optional.of(Integer.MAX_VALUE);
        Map<String, RegisteredQueryHandler<?, ?>> handlers = new HashMap<>();
        handlers.put("queryDelegate", new RegisteredQueryHandler<Void, SampleClass>("queryDelegate",
                (from, message) -> empty(), SampleClass.class));
        QueryHandler<String, SampleClass> handler = (message) -> just("OK");
        handlers.put("queryDirect", new RegisteredQueryHandler<>("queryDirect",
                (from, message) -> handler.handle(message), SampleClass.class));
        HandlerResolver resolver = new HandlerResolver(handlers, null, null, null, null);
        genericMessageListener = new ApplicationQueryListener(reactiveMessageListener, "queue", resolver, sender,
                "directExchange", messageConverter, "replyExchange", false, 1, 100, maxLengthBytes, discardNotifier, errorReporter);
    }

    @Test
    void shouldExecuteDelegateHandler() {
        Function<Message, Mono<Object>> handler = genericMessageListener.rawMessageHandler("queryDelegate");
        Message message = TestStubs.mockMessage();
        Mono<Object> result = handler.apply(message);

        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void shouldExecuteDirectHandler() {
        Function<Message, Mono<Object>> handler = genericMessageListener.rawMessageHandler("queryDirect");
        Message message = TestStubs.mockMessage();
        Mono<Object> result = handler.apply(message);

        StepVerifier.create(result)
                .expectNext("OK")
                .verifyComplete();
    }

    @Test
    void shouldHandleErrorWhenNoQueryHandler() {
        Function<Message, Mono<Object>> handler = genericMessageListener.rawMessageHandler("nonExistent");
        Message message = TestStubs.mockMessage();
        Mono<Object> result = handler.apply(message);

        StepVerifier.create(result)
                .verifyErrorMessage("Handler Not registered for Query: nonExistent");
    }

    @Test
    void shouldNotRespondQueryEnrichPostProcess() {
        Message message = spy(TestStubs.mockMessage());
        Function<Mono<Object>, Mono<Object>> handler = genericMessageListener.enrichPostProcess(message);
        Mono<Object> result = handler.apply(empty());

        StepVerifier.create(result)
                .verifyComplete();

        verify(message, times(0)).getProperties();
    }

    @Test
    void shouldRespondQueryEnrichPostProcess() {
        Message message = spy(TestStubs.mockMessage());
        Function<Mono<Object>, Mono<Object>> handler = genericMessageListener.enrichPostProcess(message);
        Mono<Object> result = handler.apply(just("OK"));
        when(sender.sendNoConfirm(any(), anyString(), anyString(), anyMap(), anyBoolean())).thenReturn(empty());

        StepVerifier.create(result)
                .verifyComplete();

        verify(message, times(2)).getProperties();
    }

    @Test
    void shouldHandleErrorWhenEnrichPostProcessSignalError() {
        Message message = TestStubs.mockMessage();
        Function<Mono<Object>, Mono<Object>> handler = genericMessageListener.enrichPostProcess(message);
        String errorMessage = "Error";
        Mono<Object> result = handler.apply(error(new RuntimeException(errorMessage)));

        StepVerifier.create(result)
                .verifyErrorMessage(errorMessage);
    }

}





