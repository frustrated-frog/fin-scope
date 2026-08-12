package com.finscope.web.messaging;

import com.finscope.domain.industrychain.IndustryChainGenerationMessage;
import com.finscope.service.industrychain.IndustryChainGenerationExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Kafka 产业图谱补全入口；业务幂等和执行状态由 service 层处理。 */
@Slf4j
@Component
public class IndustryChainGenerationListener {
    private final IndustryChainGenerationExecutor executor;

    public IndustryChainGenerationListener(IndustryChainGenerationExecutor executor) {
        this.executor = executor;
    }

    @KafkaListener(
            topics = "${finscope.industry-chain.kafka.topic:finscope.industry-chain.structure-completion.requested}",
            groupId = "${finscope.industry-chain.kafka.group-id:finscope-industry-chain-completion}",
            autoStartup = "${finscope.industry-chain.kafka.enabled:false}")
    public void consume(IndustryChainGenerationMessage message) {
        if (message == null || !message.isValid()) {
            log.warn("Ignoring invalid industry-chain generation message");
            return;
        }
        executor.executeRequested(message.getChainId(), message.getRevisionId(), message.getEventId());
    }
}
