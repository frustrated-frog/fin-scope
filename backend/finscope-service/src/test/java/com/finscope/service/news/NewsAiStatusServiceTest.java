package com.finscope.service.news;

import com.finscope.dao.news.NewsAiRepository;
import com.finscope.rpc.llm.NewsAiLunaClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NewsAiStatusServiceTest {
    @Test
    void unlimitedStatusKeepsUsageWithoutReportingAnExhaustedBalance() {
        var properties = new NewsAiProperties();
        properties.validate();
        var repository = mock(NewsAiRepository.class);
        when(repository.dailyUsage(any())).thenReturn(Map.of("calls", 3L, "input_tokens", 200L,
                "output_tokens", 100L, "actual_micros", 2_000_000L, "unresolved_micros", 500_000L));
        var client = mock(NewsAiLunaClient.class);
        when(client.model()).thenReturn("gpt-6-luna");
        var service = new NewsAiStatusService();
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "client", client);
        var status = service.status();
        assertNull(status.getDailyBudgetMicros());
        assertNull(status.getDailyMaxCalls());
        assertNull(status.getRemainingMicros());
        assertEquals(3L, status.getCalls());
        assertEquals(2_000_000L, status.getActualMicros());
        properties.setDailyBudgetMicros(3_000_000L);
        assertEquals(500_000L, service.status().getRemainingMicros());
        properties.setDailyBudgetMicros(-1L);
        assertThrows(IllegalArgumentException.class, properties::validate);
    }
}
