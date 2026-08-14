package com.finscope.web.messaging;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.radar.RadarInterpretationBatchMessage;
import com.finscope.service.radar.RadarEventInterpretationService;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RadarInterpretationBatchListenerTest {
    @Test
    void usesTheRadarSpecificListenerContainer() throws Exception {
        KafkaListener listener = RadarInterpretationBatchListener.class
                .getMethod("consume", RadarInterpretationBatchMessage.class)
                .getAnnotation(KafkaListener.class);

        assertEquals("radarKafkaListenerContainerFactory", listener.containerFactory());
    }

    @Test
    void requestsEachUniqueEventInBatchOrder() {
        RadarEventInterpretationService interpretations = mock(RadarEventInterpretationService.class);
        RadarInterpretationBatchListener listener = new RadarInterpretationBatchListener(interpretations);

        listener.consume(new RadarInterpretationBatchMessage("run-1", LocalDateTime.now(),
                Arrays.asList(10L, 10L, 20L)));

        org.mockito.InOrder order = inOrder(interpretations);
        order.verify(interpretations).request(10L);
        order.verify(interpretations).request(20L);
        verify(interpretations, times(2)).request(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void skipsInvalidEventAndContinuesTheBatch() {
        RadarEventInterpretationService interpretations = mock(RadarEventInterpretationService.class);
        doThrow(new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID)).when(interpretations).request(10L);
        RadarInterpretationBatchListener listener = new RadarInterpretationBatchListener(interpretations);

        listener.consume(new RadarInterpretationBatchMessage("run-2", LocalDateTime.now(),
                Arrays.asList(10L, 20L)));

        verify(interpretations).request(20L);
    }
}
