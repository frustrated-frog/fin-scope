package com.finscope.web.messaging;

import com.finscope.domain.quant.discovery.StockDiscoveryRequestedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StockDiscoveryListenerTest {
    @Test
    void usesTheStockDiscoverySpecificListenerContainer() throws Exception {
        KafkaListener listener = StockDiscoveryListener.class
                .getMethod("consume", StockDiscoveryRequestedEvent.class)
                .getAnnotation(KafkaListener.class);

        assertEquals("stockDiscoveryKafkaListenerContainerFactory", listener.containerFactory());
    }
}
