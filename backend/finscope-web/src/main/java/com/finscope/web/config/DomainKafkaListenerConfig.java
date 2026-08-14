package com.finscope.web.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.function.BiFunction;

@Configuration
public class DomainKafkaListenerConfig {
    @Autowired
    private ConsumerFactory<Object, Object> consumerFactory;
    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;
    @Value("${finscope.stock-discovery.kafka.dlt-topic:finscope.quant.stock-discovery.requested.dlt}")
    private String stockDiscoveryDltTopic;
    @Value("${finscope.stock-discovery.kafka.retry-interval-ms:5000}")
    private long stockDiscoveryRetryIntervalMs;
    @Value("${finscope.stock-discovery.kafka.retry-attempts:2}")
    private long stockDiscoveryRetryAttempts;
    @Value("${finscope.radar.kafka.dlt-topic:finscope.radar.interpretation.requested.dlt}")
    private String radarDltTopic;
    @Value("${finscope.radar.kafka.retry-interval-ms:1000}")
    private long radarRetryIntervalMs;
    @Value("${finscope.radar.kafka.retry-attempts:1}")
    private long radarRetryAttempts;

    @Bean(name = "stockDiscoveryKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<Object, Object> stockDiscoveryKafkaListenerContainerFactory() {
        return listenerFactory(this::stockDiscoveryDeadLetter,
                stockDiscoveryRetryIntervalMs, stockDiscoveryRetryAttempts);
    }

    @Bean(name = "radarKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<Object, Object> radarKafkaListenerContainerFactory() {
        return listenerFactory(this::radarDeadLetter, radarRetryIntervalMs, radarRetryAttempts);
    }

    TopicPartition stockDiscoveryDeadLetter(ConsumerRecord<?, ?> record, Exception error) {
        return new TopicPartition(stockDiscoveryDltTopic, record.partition());
    }

    TopicPartition radarDeadLetter(ConsumerRecord<?, ?> record, Exception error) {
        return new TopicPartition(radarDltTopic, record.partition());
    }

    private ConcurrentKafkaListenerContainerFactory<Object, Object> listenerFactory(
            BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> destination,
            long retryIntervalMs,
            long retryAttempts) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate, destination);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer, new FixedBackOff(Math.max(0L, retryIntervalMs), Math.max(0L, retryAttempts)));
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<Object, Object>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
