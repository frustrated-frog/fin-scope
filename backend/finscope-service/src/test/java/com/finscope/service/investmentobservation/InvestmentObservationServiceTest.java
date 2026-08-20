package com.finscope.service.investmentobservation;

import com.finscope.common.enums.investmentobservation.InvestmentObservationDisposition;
import com.finscope.common.enums.investmentobservation.InvestmentObservationStage;
import com.finscope.common.exception.BusinessException;
import com.finscope.dao.investmentobservation.InvestmentObservationRepository;
import com.finscope.domain.investmentobservation.InvestmentObservation;
import com.finscope.domain.investmentobservation.InvestmentObservationRefreshResult;
import com.finscope.domain.investmentobservation.InvestmentObservationWorkspace;
import com.finscope.domain.radar.RadarEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvestmentObservationServiceTest {
    private InvestmentObservationRepository repository;
    private InvestmentObservationCandidateService candidates;
    private InvestmentObservationScoringService scoring;
    private InvestmentObservationService service;
    private final LocalDateTime now = LocalDateTime.of(2026, 8, 20, 10, 0);

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(InvestmentObservationRepository.class);
        candidates = Mockito.mock(InvestmentObservationCandidateService.class);
        scoring = Mockito.mock(InvestmentObservationScoringService.class);
        service = new InvestmentObservationService();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "candidates", candidates);
        ReflectionTestUtils.setField(service, "scoring", scoring);
        ReflectionTestUtils.setField(service, "lifecycle", new InvestmentObservationLifecycleService());
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(
                Instant.parse("2026-08-20T02:00:00Z"), ZoneId.of("Asia/Shanghai")));
    }

    @Test
    void leavesTheExistingWorkspaceUntouchedWhenRadarHasNoCandidates() {
        when(candidates.load()).thenReturn(Collections.<RadarEvent>emptyList());
        when(repository.findAll()).thenReturn(Collections.singletonList(observation(1L, 66,
                InvestmentObservationStage.TRACKING, InvestmentObservationDisposition.ACTIVE)));

        InvestmentObservationRefreshResult result = service.refresh();

        assertEquals(0, result.getScannedCount());
        assertEquals(1, result.getPreservedCount());
        verify(repository, never()).upsertGenerated(any(InvestmentObservation.class), any(LocalDateTime.class));
    }

    @Test
    void boundsEachRefreshToTwentyHighestScoringCandidates() {
        List<RadarEvent> events = new ArrayList<RadarEvent>();
        for (int index = 1; index <= 25; index++) {
            RadarEvent event = new RadarEvent();
            event.setId((long) index);
            events.add(event);
        }
        when(candidates.load()).thenReturn(events);
        when(scoring.score(any(RadarEvent.class))).thenAnswer(invocation -> {
            RadarEvent event = invocation.getArgument(0);
            return observation(event.getId(), (int) (100 - event.getId()), InvestmentObservationStage.TRACKING,
                    InvestmentObservationDisposition.ACTIVE);
        });
        when(repository.upsertGenerated(any(InvestmentObservation.class), any(LocalDateTime.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findAll()).thenReturn(Collections.<InvestmentObservation>emptyList());

        InvestmentObservationRefreshResult result = service.refresh();

        assertEquals(25, result.getScannedCount());
        assertEquals(20, result.getUpdatedCount());
        verify(repository, Mockito.times(20)).upsertGenerated(any(InvestmentObservation.class), any(LocalDateTime.class));
        verify(scoring).applyFocusFloor(any());
    }

    @Test
    void keepsStoredObservationsWhenCandidateReadingFails() {
        when(candidates.load()).thenThrow(new IllegalStateException("雷达快照暂不可用"));

        assertThrows(IllegalStateException.class, () -> service.refresh());
        verify(repository, never()).upsertGenerated(any(InvestmentObservation.class), any(LocalDateTime.class));
    }

    @Test
    void assemblesIndependentWorkspaceStagesAndHidesIgnoredItemsFromActiveCounts() {
        InvestmentObservation focus = observation(1L, 82, InvestmentObservationStage.FOCUS,
                InvestmentObservationDisposition.ACTIVE);
        focus.setLastChangedAt(now.minusHours(1));
        InvestmentObservation later = observation(2L, 62, InvestmentObservationStage.TRACKING,
                InvestmentObservationDisposition.LATER);
        later.setLastChangedAt(now.minusDays(1));
        InvestmentObservation ignored = observation(3L, 78, InvestmentObservationStage.FOCUS,
                InvestmentObservationDisposition.IGNORED);
        ignored.setLastChangedAt(now);
        when(repository.findAll()).thenReturn(java.util.Arrays.asList(focus, later, ignored));
        when(repository.findRecentTransitions(anyInt())).thenReturn(Collections.emptyList());

        InvestmentObservationWorkspace workspace = service.workspace();

        assertEquals(1, workspace.getFocus().size());
        assertEquals(1, workspace.getTracking().size());
        assertEquals(2, workspace.getActiveCount());
        assertEquals(1, workspace.getChangedTodayCount());
    }

    @Test
    void rejectsAStaleUserStateRevision() {
        when(repository.findById(7L)).thenReturn(java.util.Optional.of(observation(7L, 60,
                InvestmentObservationStage.TRACKING, InvestmentObservationDisposition.ACTIVE)));
        when(repository.updateDisposition(7L, InvestmentObservationDisposition.LATER, 1, now)).thenReturn(false);

        assertThrows(BusinessException.class,
                () -> service.updateDisposition(7L, InvestmentObservationDisposition.LATER, 1));
    }

    private InvestmentObservation observation(Long id, int score, InvestmentObservationStage stage,
                                              InvestmentObservationDisposition disposition) {
        InvestmentObservation value = new InvestmentObservation();
        value.setId(id);
        value.setSourceId(id);
        value.setTitle("观察对象 " + id);
        value.setScore(score);
        value.setStage(stage);
        value.setDisposition(disposition);
        value.setRevision(1);
        value.setNextValidation("等待经营数据确认");
        value.setUpdatedAt(now);
        return value;
    }
}
