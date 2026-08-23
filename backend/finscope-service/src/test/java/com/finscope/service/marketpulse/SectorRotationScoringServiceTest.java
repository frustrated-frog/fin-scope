package com.finscope.service.marketpulse;

import com.finscope.common.enums.marketpulse.SectorRotationStage;
import com.finscope.domain.marketpulse.SectorRotationItem;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectorRotationScoringServiceTest {
    private final SectorRotationScoringService service = new SectorRotationScoringService();

    @Test
    void distinguishesAccelerationFromOverheatedCrowding() {
        SectorRotationItem accelerating = sector("创新药", 2.4D, 4.8D, 0.75D, 2, 8, 58);
        SectorRotationItem overheated = sector("贵金属", 3.5D, 9.2D, 0.83D, 1, 5, 82);

        List<SectorRotationItem> values = service.score(Arrays.asList(accelerating, overheated));

        assertEquals(SectorRotationStage.OVERHEATED, values.get(0).getStage());
        assertEquals(SectorRotationStage.ACCELERATING, values.get(1).getStage());
        assertTrue(values.get(0).getExplanations().stream().anyMatch(value -> value.contains("拥挤")));
    }

    @Test
    void keepsMissingHistoryVisibleButIneligibleForRotationRanking() {
        SectorRotationItem item = sector("新板块", 4.2D, null, 0.9D, 1, 0, 30);

        SectorRotationItem result = service.score(Arrays.asList(item)).get(0);

        assertEquals(SectorRotationStage.INSUFFICIENT_DATA, result.getStage());
        assertTrue(result.getRotationScore() < 40);
    }

    @Test
    void ranksAvailableReturnHistoryWithoutInventingMissingBreadth() {
        SectorRotationItem item = sector("医药生物", 2.1D, 4.5D, 0.7D, 2, 3, 55);
        item.setBreadthRatio(null);

        SectorRotationItem result = service.score(Arrays.asList(item)).get(0);

        assertTrue(result.getRotationScore() >= 40);
        assertTrue(result.getExplanations().stream().anyMatch(value -> value.contains("行业宽度未接入")));
    }

    private SectorRotationItem sector(String name, double return1d, Double return5d, double breadth,
                                      int flowRank, int persistence, int crowding) {
        SectorRotationItem value = new SectorRotationItem();
        value.setSectorCode(name);
        value.setSectorName(name);
        value.setReturn1d(return1d);
        value.setReturn5d(return5d);
        value.setExcessReturn5d(return5d == null ? null : return5d - 0.5D);
        value.setBreadthRatio(breadth);
        value.setFlowRank(flowRank);
        value.setPreviousFlowRank(flowRank + 4);
        value.setPersistenceDays(persistence);
        value.setCrowdingScore(crowding);
        return value;
    }
}
