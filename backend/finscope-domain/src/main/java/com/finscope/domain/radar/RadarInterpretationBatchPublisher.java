package com.finscope.domain.radar;

public interface RadarInterpretationBatchPublisher {
    void publish(RadarInterpretationBatchMessage message);
}
