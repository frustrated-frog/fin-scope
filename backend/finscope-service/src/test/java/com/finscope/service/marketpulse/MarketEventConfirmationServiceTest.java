package com.finscope.service.marketpulse;

import com.finscope.common.enums.marketpulse.MarketEventConfirmationState;
import com.finscope.common.enums.marketpulse.SectorRotationStage;
import com.finscope.domain.marketpulse.MarketEventConfirmation;
import com.finscope.domain.marketpulse.SectorRotationItem;
import com.finscope.domain.radar.RadarEvent;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketEventConfirmationServiceTest {
    private final MarketEventConfirmationService service = new MarketEventConfirmationService();

    @Test
    void mapsDirectSectorMentionsAndClassifiesStrongNewsWithStrongMarketReaction() {
        RadarEvent event = event(91, "医药生物创新药临床数据取得进展");
        SectorRotationItem sector = sector("医药生物", 82, 3.6D);

        List<MarketEventConfirmation> values = service.confirm(Arrays.asList(event), Arrays.asList(sector));

        assertEquals(1, values.size());
        assertEquals(MarketEventConfirmationState.CONFIRMED, values.get(0).getConfirmationState());
        assertTrue(values.get(0).isEligibleForRanking());
        assertEquals("DIRECT_MENTION", values.get(0).getMappingSource());
    }

    @Test
    void doesNotInventSemanticLinksWhenSectorNameIsAbsent() {
        RadarEvent event = event(88, "海外临床研究出现积极进展");

        List<MarketEventConfirmation> values = service.confirm(
                Arrays.asList(event), Arrays.asList(sector("医药生物", 80, 3D)));

        assertTrue(values.isEmpty());
    }

    private RadarEvent event(int score, String title) {
        RadarEvent value = new RadarEvent();
        value.setId(7L);
        value.setCanonicalTitle(title);
        value.setSummary("事件获得多个独立来源确认");
        value.setHotspotScore(score);
        value.setConfidenceScore(86);
        return value;
    }

    private SectorRotationItem sector(String name, int score, double return1d) {
        SectorRotationItem value = new SectorRotationItem();
        value.setSectorCode("881144");
        value.setSectorName(name);
        value.setRotationScore(score);
        value.setReturn1d(return1d);
        value.setStage(SectorRotationStage.ACCELERATING);
        return value;
    }
}
