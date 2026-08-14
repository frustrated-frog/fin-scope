package com.finscope.rpc.quant;

import com.finscope.domain.quant.discovery.StockDiscoveryEventPublisher;
import com.finscope.domain.quant.discovery.StockDiscoveryRequestedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class KafkaStockDiscoveryEventPublisher implements StockDiscoveryEventPublisher {
    @Resource
    private KafkaTemplate<String, StockDiscoveryRequestedEvent> kafka;
    @Value("${finscope.stock-discovery.kafka.enabled:true}")
    private boolean enabled;
    @Value("${finscope.stock-discovery.kafka.topic:finscope.quant.stock-discovery.requested}")
    private String topic;
    @Value("${finscope.stock-discovery.kafka.ack-timeout-ms:2000}")
    private long acknowledgementTimeoutMs;

    @Override
    public boolean publish(StockDiscoveryRequestedEvent event) {
        if (!enabled || event == null || event.getRunId() == null) {
            return false;
        }
        try {
            kafka.send(topic, event.getRunKey(), event)
                    .get(acknowledgementTimeoutMs, TimeUnit.MILLISECONDS);
            return true;
        } catch (Exception error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("股票发现任务发送失败，runKey={}", event.getRunKey(), error);
            return false;
        }
    }
}
