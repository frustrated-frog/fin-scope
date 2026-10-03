package com.finscope.service.marketpulse;

import com.finscope.dao.marketpulse.MarketPulseRepository;
import com.finscope.domain.marketpulse.MarketPulseWorkspace;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MarketPanoramaServiceTest {
    @Test
    void readsBoundedChronologicalSnapshotsWithoutFutureFrames() {
        var repository = mock(MarketPulseRepository.class);
        var service = new MarketPanoramaService();
        ReflectionTestUtils.setField(service, "repository", repository);
        LocalDate date = LocalDate.of(2026, 9, 30);
        when(repository.findRecentWorkspaces(20, date)).thenReturn(List.of(
                frame(date), frame(date.plusDays(1)), frame(date.minusDays(1))));
        assertEquals(List.of(date.minusDays(1), date), service.history(date, 20).stream()
                .map(MarketPulseWorkspace::getBusinessDate).toList());
        verify(repository).findRecentWorkspaces(20, date);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void rejectsUnboundedRequestsBeforeReadingAndAllowsEmptyHistory() {
        var repository = mock(MarketPulseRepository.class);
        var service = new MarketPanoramaService();
        ReflectionTestUtils.setField(service, "repository", repository);
        LocalDate date = LocalDate.of(2026, 9, 30);
        assertThrows(IllegalArgumentException.class, () -> service.history(date, 61));
        assertThrows(IllegalArgumentException.class, () -> service.history(date, 0));
        assertThrows(IllegalArgumentException.class, () -> service.history(null, 20));
        verifyNoInteractions(repository);
        when(repository.findRecentWorkspaces(60, date)).thenReturn(List.of());
        assertTrue(service.history(date, 60).isEmpty());
    }

    private MarketPulseWorkspace frame(LocalDate date) {
        var frame = new MarketPulseWorkspace();
        frame.setBusinessDate(date);
        return frame;
    }
}
