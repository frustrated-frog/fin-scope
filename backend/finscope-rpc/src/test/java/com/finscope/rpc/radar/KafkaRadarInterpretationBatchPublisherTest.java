package com.finscope.rpc.radar;

import com.finscope.domain.radar.RadarInterpretationBatchMessage;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.concurrent.SettableListenableFuture;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaRadarInterpretationBatchPublisherTest {
    @Test
    void sendsEnabledBatchWithRunKeyAsKafkaKey() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, RadarInterpretationBatchMessage> kafka = mock(KafkaTemplate.class);
        KafkaRadarInterpretationBatchPublisher publisher = new KafkaRadarInterpretationBatchPublisher();
        ReflectionTestUtils.setField(publisher, "kafka", kafka);
        ReflectionTestUtils.setField(publisher, "enabled", true);
        ReflectionTestUtils.setField(publisher, "topic", "finscope.radar.interpretation.requested");
        RadarInterpretationBatchMessage message = new RadarInterpretationBatchMessage("run-1",
                LocalDateTime.of(2026, 8, 12, 10, 0), Arrays.asList(1L, 2L));
        when(kafka.send("finscope.radar.interpretation.requested", "run-1", message))
                .thenReturn(new SettableListenableFuture<>());

        publisher.publish(message);

        verify(kafka).send("finscope.radar.interpretation.requested", "run-1", message);
    }

    @Test
    void doesNotContactKafkaWhenFeatureIsDisabled() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, RadarInterpretationBatchMessage> kafka = mock(KafkaTemplate.class);
        KafkaRadarInterpretationBatchPublisher publisher = new KafkaRadarInterpretationBatchPublisher();
        ReflectionTestUtils.setField(publisher, "kafka", kafka);
        ReflectionTestUtils.setField(publisher, "enabled", false);
        ReflectionTestUtils.setField(publisher, "topic", "finscope.radar.interpretation.requested");

        publisher.publish(new RadarInterpretationBatchMessage("run-2", LocalDateTime.now(), Arrays.asList(1L)));

        verify(kafka, never()).send(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(RadarInterpretationBatchMessage.class));
    }
}
