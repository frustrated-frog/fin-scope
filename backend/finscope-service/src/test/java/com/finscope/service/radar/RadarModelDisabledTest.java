package com.finscope.service.radar;

import com.finscope.service.news.NewsWorkbenchCapabilities;
import com.finscope.domain.radar.RadarEvent;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import static org.mockito.Mockito.*;

class RadarModelDisabledTest {
    @Test
    void doesNotSubmitTitleEvidenceOrPairTasks() {
        NewsWorkbenchCapabilities capabilities = mock(NewsWorkbenchCapabilities.class);
        Executor executor = mock(Executor.class);
        RadarEventEnhancementScheduler enhancement = new RadarEventEnhancementScheduler();
        RadarPairDecisionScheduler pairs = new RadarPairDecisionScheduler();
        ReflectionTestUtils.setField(enhancement, "capabilities", capabilities);
        ReflectionTestUtils.setField(enhancement, "executor", executor);
        ReflectionTestUtils.setField(pairs, "capabilities", capabilities);
        ReflectionTestUtils.setField(pairs, "executor", executor);
        RadarEvent event = new RadarEvent();
        event.setId(1L);
        enhancement.schedule(event, List.of(), LocalDateTime.now(), true);
        pairs.schedule(null, null, "left", "right");
        verifyNoInteractions(executor);
    }
}
