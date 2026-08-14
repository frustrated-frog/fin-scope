package com.finscope.web.messaging;

import com.finscope.domain.quant.discovery.StockDiscoveryRequestedEvent;
import com.finscope.service.quant.discovery.StockDiscoveryService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class StockDiscoveryListener {
    @Resource
    private StockDiscoveryService service;

    @KafkaListener(topics = "${finscope.stock-discovery.kafka.topic:finscope.quant.stock-discovery.requested}",
            groupId = "${finscope.stock-discovery.kafka.group-id:finscope-stock-discovery}",
            containerFactory = "stockDiscoveryKafkaListenerContainerFactory",
            autoStartup = "${finscope.stock-discovery.kafka.enabled:true}")
    public void consume(StockDiscoveryRequestedEvent event) {
        service.execute(event);
    }
}
