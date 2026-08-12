package com.finscope.web.messaging;

import com.finscope.common.exception.BusinessException;
import com.finscope.domain.radar.RadarInterpretationBatchMessage;
import com.finscope.service.radar.RadarEventInterpretationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class RadarInterpretationBatchListener {
    private static final Logger log = LoggerFactory.getLogger(RadarInterpretationBatchListener.class);

    private final RadarEventInterpretationService interpretations;

    public RadarInterpretationBatchListener(RadarEventInterpretationService interpretations) {
        this.interpretations = interpretations;
    }

    @KafkaListener(
            topics = "${finscope.radar.kafka.topic:finscope.radar.interpretation.requested}",
            groupId = "${finscope.radar.kafka.group-id:finscope-radar-interpretation}",
            autoStartup = "${finscope.radar.kafka.enabled:false}")
    public void consume(RadarInterpretationBatchMessage message) {
        if (message == null) return;
        for (Long eventId : message.getEventIds()) {
            try {
                interpretations.request(eventId);
            } catch (BusinessException error) {
                log.warn("跳过无法预解读的雷达事件，runKey={} eventId={} reason={}",
                        message.getRunKey(), eventId, error.getMessage());
            }
        }
    }
}
