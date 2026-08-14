package com.finscope.web.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DomainKafkaListenerConfigTest {
    @Test
    void routesEachDomainToItsOwnDeadLetterTopic() {
        DomainKafkaListenerConfig config = new DomainKafkaListenerConfig();
        ReflectionTestUtils.setField(config, "stockDiscoveryDltTopic", "stock-discovery.dlt");
        ReflectionTestUtils.setField(config, "radarDltTopic", "radar.dlt");
        ConsumerRecord<Object, Object> record = new ConsumerRecord<Object, Object>(
                "source", 0, 1L, "key", "value");

        assertEquals("stock-discovery.dlt",
                config.stockDiscoveryDeadLetter(record, new IllegalStateException()).topic());
        assertEquals("radar.dlt",
                config.radarDeadLetter(record, new IllegalStateException()).topic());
    }
}
