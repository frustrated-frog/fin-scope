package com.finscope.service.dashboard;

import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.service.radar.RadarDashboardCategoryService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardHotspotRankingServiceTest {
    @Test
    void returnsThreeBoardsWithReadableEventContent() {
        RadarRepository repository = mock(RadarRepository.class);
        RadarEvent finance = event(11L, "央行宣布降准", "本次调整预计释放长期流动性约一万亿元", 91);
        when(repository.findTopByDashboardCategory("FINANCE", 5)).thenReturn(Collections.singletonList(finance));
        when(repository.findTopByDashboardCategory("TECHNOLOGY", 5)).thenReturn(Collections.emptyList());
        when(repository.findTopByDashboardCategory("POLITICS", 5)).thenReturn(Collections.emptyList());

        DashboardHotspotRankingService service = new DashboardHotspotRankingService(
                repository, new RadarDashboardCategoryService());
        List<DashboardHotspotRankingService.Ranking> rankings = service.rankings();

        assertEquals(3, rankings.size());
        assertEquals("FINANCE", rankings.get(0).getCategoryCode());
        assertEquals("金融", rankings.get(0).getLabel());
        assertEquals(1, rankings.get(0).getItems().size());
        assertEquals("央行宣布降准", rankings.get(0).getItems().get(0).getTitle());
        assertEquals("本次调整预计释放长期流动性约一万亿元", rankings.get(0).getItems().get(0).getSummary());
        assertEquals(91, rankings.get(0).getItems().get(0).getHotspotScore());
        assertEquals("RISING", rankings.get(0).getItems().get(0).getLifecycleState());
        assertEquals(3, rankings.get(0).getItems().get(0).getSourceCount());
        verify(repository).findTopByDashboardCategory("FINANCE", 5);
        verify(repository).findTopByDashboardCategory("TECHNOLOGY", 5);
        verify(repository).findTopByDashboardCategory("POLITICS", 5);
    }

    @Test
    void classifiesEventsCreatedBeforeTheDashboardCategoryMigration() {
        RadarRepository repository = mock(RadarRepository.class);
        RadarEvent unclassified = event(12L, "OpenAI 发布新一代模型", "Agent 推理能力明显提升", 86);
        unclassified.setDashboardCategory("UNCLASSIFIED");
        when(repository.findEventsForDashboardClassification(500)).thenReturn(Collections.singletonList(unclassified));
        when(repository.findTopByDashboardCategory("FINANCE", 5)).thenReturn(Collections.emptyList());
        when(repository.findTopByDashboardCategory("TECHNOLOGY", 5)).thenReturn(Collections.emptyList());
        when(repository.findTopByDashboardCategory("POLITICS", 5)).thenReturn(Collections.emptyList());

        DashboardHotspotRankingService service = new DashboardHotspotRankingService(
                repository, new RadarDashboardCategoryService());
        service.rankings();

        verify(repository).updateDashboardCategory(12L, "TECHNOLOGY");
    }

    @Test
    void repairsPreviouslyMisclassifiedActiveEvents() {
        RadarRepository repository = mock(RadarRepository.class);
        RadarEvent marketMove = event(13L, "AI应用端反复走强 博彦科技2连板", "AI应用端反复走强，博彦科技涨停。", 86);
        marketMove.setCategoryCode("MARKET_MOVE");
        marketMove.setDashboardCategory("TECHNOLOGY");
        when(repository.findEventsForDashboardClassification(500)).thenReturn(Collections.singletonList(marketMove));
        when(repository.findTopByDashboardCategory("FINANCE", 5)).thenReturn(Collections.emptyList());
        when(repository.findTopByDashboardCategory("TECHNOLOGY", 5)).thenReturn(Collections.emptyList());
        when(repository.findTopByDashboardCategory("POLITICS", 5)).thenReturn(Collections.emptyList());

        DashboardHotspotRankingService service = new DashboardHotspotRankingService(
                repository, new RadarDashboardCategoryService());
        service.rankings();

        verify(repository).updateDashboardCategory(13L, "FINANCE");
    }

    private RadarEvent event(Long id, String title, String summary, int score) {
        RadarEvent event = new RadarEvent();
        event.setId(id);
        event.setCanonicalTitle(title);
        event.setSummary(summary);
        event.setHotspotScore(score);
        event.setHotspotLifecycleState("RISING");
        event.setSourceCount(3);
        event.setSignalCount(5);
        event.setLastSeenAt(LocalDateTime.of(2026, 8, 6, 9, 30));
        return event;
    }
}
