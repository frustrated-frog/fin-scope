package com.finscope.rpc.radar;

import com.finscope.domain.radar.RadarInterpretationBatchMessage;
import com.finscope.domain.radar.RadarInterpretationBatchPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class KafkaRadarInterpretationBatchPublisher implements RadarInterpretationBatchPublisher {
    private static final Logger log = LoggerFactory.getLogger(KafkaRadarInterpretationBatchPublisher.class);

    @Resource
    private KafkaTemplate<String, RadarInterpretationBatchMessage> kafka;
    @Value("${finscope.radar.kafka.enabled:false}")
    private boolean enabled;
    @Value("${finscope.radar.kafka.topic:finscope.radar.interpretation.requested}")
    private String topic;


    @Override
    public void publish(RadarInterpretationBatchMessage message) {
        if (!enabled || message == null || message.getEventIds().isEmpty()) return;
        kafka.send(topic, message.getRunKey(), message).addCallback(
                result -> { },
                error -> log.warn("Kafka 雷达预解读消息异步发送失败，runKey={}", message.getRunKey(), error));
    }
}
