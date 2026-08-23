package com.finscope.service.marketpulse;

import com.finscope.common.enums.marketpulse.MarketEventConfirmationState;
import com.finscope.common.enums.marketpulse.MarketLiquidityState;
import com.finscope.common.enums.marketpulse.MarketPulseQualityStatus;
import com.finscope.common.enums.marketpulse.MarketStage;
import com.finscope.common.enums.marketpulse.SectorRotationStage;
import com.finscope.domain.marketpulse.DailyMarketReview;
import com.finscope.domain.marketpulse.MarketBreadthSnapshot;
import com.finscope.domain.marketpulse.MarketEventConfirmation;
import com.finscope.domain.marketpulse.MarketIndexPerformance;
import com.finscope.domain.marketpulse.MarketPulseWorkspace;
import com.finscope.domain.marketpulse.MarketRegimeFeatures;
import com.finscope.domain.marketpulse.MarketRegimeSnapshot;
import com.finscope.domain.marketpulse.SectorRotationItem;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DailyMarketReviewServiceTest {
    private final DailyMarketReviewService service = new DailyMarketReviewService();

    @Test
    void explainsAContractingPostSellOffRepairWithConfirmedLeaders() {
        MarketPulseWorkspace workspace = workspace(0.62D, 0.006D, 0.81D);
        workspace.setSectors(Arrays.asList(
                sector("创新药", 2.1D, 5.8D, 78, SectorRotationStage.PERSISTENT),
                sector("半导体", -1.4D, -3.2D, 28, SectorRotationStage.WEAK),
                sector("贵金属", 3.2D, 9.1D, 82, SectorRotationStage.OVERHEATED)));
        MarketEventConfirmation event = new MarketEventConfirmation();
        event.setTitle("mRNA 肿瘤疫苗临床数据更新");
        event.setSectorName("创新药");
        event.setEventScore(82);
        event.setMarketReactionScore(78);
        event.setConfirmationState(MarketEventConfirmationState.CONFIRMED);
        workspace.setEventConfirmations(Collections.singletonList(event));

        DailyMarketReview review = service.generate(workspace);

        assertTrue(review.getHeadline().contains("缩量修复"));
        assertTrue(review.getIndexOverview().contains("创业板指"));
        assertTrue(review.getBreadthConclusion().contains("62%"));
        assertTrue(review.getLeadingSectors().stream().anyMatch(value -> value.contains("创新药")));
        assertTrue(review.getWeakeningSectors().stream().anyMatch(value -> value.contains("半导体")));
        assertTrue(review.getConfirmedEvents().stream().anyMatch(value -> value.contains("mRNA")));
        assertTrue(review.getRiskSignals().stream().anyMatch(value -> value.contains("量能")));
        assertTrue(review.getRiskSignals().stream().anyMatch(value -> value.contains("研究候选")));
        assertTrue(review.getNextSessionWatchlist().stream().anyMatch(value -> value.contains("成交")));
        assertTrue(review.getEvidence().stream().anyMatch(value -> value.contains("上涨比例")));
    }

    @Test
    void flagsPositiveIndexReturnWhenMarketBreadthContracts() {
        MarketPulseWorkspace workspace = workspace(0.30D, 0.008D, 1.02D);

        DailyMarketReview review = service.generate(workspace);

        assertTrue(review.getRiskSignals().stream().anyMatch(value -> value.contains("宽度背离")));
        assertTrue(review.getBreadthConclusion().contains("多数个股承压"));
    }

    @Test
    void fingerprintsAllFactsAndIgnoresSectorInputOrder() {
        MarketPulseWorkspace first = workspace(0.62D, 0.006D, 0.81D);
        SectorRotationItem medicine = sector("创新药", 2.1D, 5.8D, 78, SectorRotationStage.PERSISTENT);
        SectorRotationItem chip = sector("半导体", -1.4D, -3.2D, 28, SectorRotationStage.WEAK);
        first.setSectors(Arrays.asList(medicine, chip));
        MarketPulseWorkspace reordered = workspace(0.62D, 0.006D, 0.81D);
        reordered.setSectors(Arrays.asList(chip, medicine));
        MarketPulseWorkspace changed = workspace(0.30D, 0.006D, 0.81D);
        changed.setSectors(Arrays.asList(medicine, chip));

        String firstFingerprint = service.generate(first).getSourceFingerprint();

        assertEquals(firstFingerprint, service.generate(reordered).getSourceFingerprint());
        assertNotEquals(firstFingerprint, service.generate(changed).getSourceFingerprint());
        assertEquals(64, firstFingerprint.length());
    }

    private MarketPulseWorkspace workspace(double advanceRatio, double marketReturn, double amountRatio) {
        LocalDate date = LocalDate.of(2026, 8, 21);
        MarketRegimeFeatures features = new MarketRegimeFeatures();
        features.setReturn1d(marketReturn);
        features.setReturn5d(-0.012D);
        features.setAmountRatio5To20(amountRatio);
        MarketRegimeSnapshot regime = new MarketRegimeSnapshot();
        regime.setBusinessDate(date);
        regime.setMarketStage(MarketStage.POST_SELL_OFF_REPAIR);
        regime.setLiquidityState(MarketLiquidityState.SHRINKING);
        regime.setFeatures(features);
        regime.setConfidenceScore(72);
        regime.setQualityStatus(MarketPulseQualityStatus.PARTIAL);
        regime.setSourceFingerprint("market-2026-08-21");

        MarketBreadthSnapshot breadth = new MarketBreadthSnapshot();
        breadth.setBusinessDate(date);
        breadth.setQualityStatus("FRESH_PRIMARY");
        breadth.setAdvanceRatio(advanceRatio);
        breadth.setAdvanceCount((int) Math.round(5100 * advanceRatio));
        breadth.setDeclineCount(5100 - breadth.getAdvanceCount());
        breadth.setFlatCount(0);
        breadth.setValidCount(5100);
        breadth.setTotalAmount(2_300_000_000_000D);
        breadth.setMedianChangePct(advanceRatio < 0.5D ? -0.7D : 0.7D);
        breadth.setLimitUpCount(68);
        breadth.setLimitDownCount(4);
        breadth.setIndices(Arrays.asList(
                index("上证指数", 0.4D),
                index("创业板指", 1.4D),
                index("沪深300", 0.6D)));

        MarketPulseWorkspace value = new MarketPulseWorkspace();
        value.setBusinessDate(date);
        value.setRegime(regime);
        value.setBreadth(breadth);
        value.setQualityStatus(MarketPulseQualityStatus.PARTIAL);
        return value;
    }

    private MarketIndexPerformance index(String name, double return1d) {
        MarketIndexPerformance value = new MarketIndexPerformance();
        value.setName(name);
        value.setReturn1d(return1d);
        return value;
    }

    private SectorRotationItem sector(String name, double return1d, double return5d,
                                      int score, SectorRotationStage stage) {
        SectorRotationItem value = new SectorRotationItem();
        value.setSectorCode(name);
        value.setSectorName(name);
        value.setReturn1d(return1d);
        value.setReturn5d(return5d);
        value.setRotationScore(score);
        value.setStage(stage);
        return value;
    }
}
