package com.finscope.rpc.industrychain;

import com.finscope.domain.industrychain.IndustryChainGenerationMessage;
import com.finscope.domain.industrychain.IndustryChainGenerationPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/** 使用 Kafka 派发产业图谱补全任务，并明确反馈是否已被 broker 接收。 */
@Slf4j
@Component
public class KafkaIndustryChainGenerationPublisher implements IndustryChainGenerationPublisher {
    private final KafkaTemplate<String, IndustryChainGenerationMessage> kafka;
    private final boolean enabled;
    private final String topic;
    private final long acknowledgementTimeoutMs;

    @Autowired
    public KafkaIndustryChainGenerationPublisher(
            KafkaTemplate<String, IndustryChainGenerationMessage> kafka,
            @Value("${finscope.industry-chain.kafka.enabled:false}") boolean enabled,
            @Value("${finscope.industry-chain.kafka.topic:finscope.industry-chain.structure-completion.requested}")
            String topic,
            @Value("${finscope.industry-chain.kafka.ack-timeout-ms:2000}") long acknowledgementTimeoutMs) {
        this.kafka = kafka;
        this.enabled = enabled;
        this.topic = topic;
        this.acknowledgementTimeoutMs = acknowledgementTimeoutMs;
    }

    @Override
    public boolean publish(IndustryChainGenerationMessage message) {
        if (!enabled || message == null || !message.isValid()) {
            return false;
        }
        try {
            kafka.send(topic, String.valueOf(message.getRevisionId()), message)
                    .get(acknowledgementTimeoutMs, TimeUnit.MILLISECONDS);
            log.info("Industry-chain generation dispatched: eventId={}, chainId={}, revisionId={}",
                    message.getEventId(), message.getChainId(), message.getRevisionId());
            return true;
        } catch (Exception error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Industry-chain Kafka dispatch failed, falling back locally: eventId={}, chainId={}, "
                            + "revisionId={}, errorType={}", message.getEventId(), message.getChainId(),
                    message.getRevisionId(), error.getClass().getSimpleName());
            return false;
        }
    }
}
