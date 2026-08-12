package com.finscope.rpc.industrychain;

import com.finscope.domain.industrychain.IndustryChainGenerationMessage;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.concurrent.SettableListenableFuture;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaIndustryChainGenerationPublisherTest {
    @Test
    void acknowledgesEnabledGenerationMessageUsingRevisionKey() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, IndustryChainGenerationMessage> kafka = mock(KafkaTemplate.class);
        SettableListenableFuture future = new SettableListenableFuture();
        future.set(null);
        IndustryChainGenerationMessage message = IndustryChainGenerationMessage.requested(7L, 11L);
        when(kafka.send("finscope.industry-chain.structure-completion.requested", "11", message))
                .thenReturn(future);
        KafkaIndustryChainGenerationPublisher publisher = new KafkaIndustryChainGenerationPublisher(
                kafka, true, "finscope.industry-chain.structure-completion.requested", 1000L);

        assertTrue(publisher.publish(message));

        verify(kafka).send("finscope.industry-chain.structure-completion.requested", "11", message);
    }

    @Test
    void returnsFalseWithoutContactingKafkaWhenDisabled() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, IndustryChainGenerationMessage> kafka = mock(KafkaTemplate.class);
        KafkaIndustryChainGenerationPublisher publisher = new KafkaIndustryChainGenerationPublisher(
                kafka, false, "topic", 1000L);

        assertFalse(publisher.publish(IndustryChainGenerationMessage.requested(7L, 11L)));

        verify(kafka, never()).send(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(IndustryChainGenerationMessage.class));
    }

    @Test
    void returnsFalseWhenBrokerAcknowledgementFails() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, IndustryChainGenerationMessage> kafka = mock(KafkaTemplate.class);
        SettableListenableFuture future = new SettableListenableFuture();
        future.setException(new IllegalStateException("broker unavailable"));
        IndustryChainGenerationMessage message = IndustryChainGenerationMessage.requested(7L, 11L);
        when(kafka.send("topic", "11", message)).thenReturn(future);
        KafkaIndustryChainGenerationPublisher publisher = new KafkaIndustryChainGenerationPublisher(
                kafka, true, "topic", TimeUnit.SECONDS.toMillis(1));

        assertFalse(publisher.publish(message));
    }
}
