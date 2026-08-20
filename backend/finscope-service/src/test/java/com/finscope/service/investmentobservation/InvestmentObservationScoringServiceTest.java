package com.finscope.service.investmentobservation;

import com.finscope.common.enums.investmentobservation.InvestmentObservationChangeType;
import com.finscope.common.enums.investmentobservation.InvestmentObservationStage;
import com.finscope.domain.investmentobservation.InvestmentObservation;
import com.finscope.domain.radar.RadarEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvestmentObservationScoringServiceTest {
    private final InvestmentObservationScoringService service = new InvestmentObservationScoringService();

    @Test
    void promotesWellConfirmedVerifiableChangeToFocus() {
        RadarEvent event = event(92, 88, 4, "RISING");
        event.setCategoryCode("COMPANY");
        event.setCanonicalTitle("公司获得大型服务器订单并上调交付指引");
        event.setEvidenceStatus("READY");
        event.setEvidenceSummary("公告与两个独立行业来源共同确认订单变化");
        event.setNextObservation("检查下一季度收入、交付量和合同负债");

        InvestmentObservation result = service.score(event);

        assertEquals(InvestmentObservationStage.FOCUS, result.getStage());
        assertTrue(result.getScore() >= 70);
        assertEquals(InvestmentObservationChangeType.ORDER, result.getChangeType());
        assertEquals(6, result.getScoreDimensions().size());
        assertFalse(result.isEvidenceInsufficient());
    }

    @Test
    void keepsModerateEvidenceInTrackingAndWeakEvidenceAsLearning() {
        RadarEvent moderate = event(58, 52, 2, "STABLE");
        moderate.setCanonicalTitle("行业需求出现改善迹象");
        moderate.setEvidenceSummary("两个来源出现方向一致的需求信号");
        moderate.setNextObservation("等待月度出货数据确认");
        RadarEvent weak = event(25, 20, 1, "NEW");
        weak.setCanonicalTitle("市场传闻某公司可能获得订单");
        weak.setUncertainty("只有单一转载来源");

        assertEquals(InvestmentObservationStage.TRACKING, service.score(moderate).getStage());
        InvestmentObservation weakResult = service.score(weak);
        assertEquals(InvestmentObservationStage.LEARNING, weakResult.getStage());
        assertTrue(weakResult.getUncertainty().contains("单一转载"));
    }

    @Test
    void handlesMissingOptionalFieldsConservatively() {
        RadarEvent event = new RadarEvent();
        event.setId(9L);
        event.setCanonicalTitle("信息仍待确认");

        InvestmentObservation result = service.score(event);

        assertEquals(InvestmentObservationStage.LEARNING, result.getStage());
        assertTrue(result.getScore() < 50);
        assertTrue(result.getNextValidation().contains("独立来源"));
        assertTrue(result.isEvidenceInsufficient());
    }

    @Test
    void keepsTheWorkspaceUsefulWhenNothingReachesTheFocusThreshold() {
        InvestmentObservation first = service.score(event(62, 58, 2, "STABLE"));
        InvestmentObservation second = service.score(event(59, 55, 2, "STABLE"));
        InvestmentObservation third = service.score(event(55, 52, 2, "STABLE"));
        InvestmentObservation fourth = service.score(event(52, 50, 2, "STABLE"));
        first.setScore(66);
        second.setScore(63);
        third.setScore(60);
        fourth.setScore(57);
        first.setStage(InvestmentObservationStage.TRACKING);
        second.setStage(InvestmentObservationStage.TRACKING);
        third.setStage(InvestmentObservationStage.TRACKING);
        fourth.setStage(InvestmentObservationStage.TRACKING);
        List<InvestmentObservation> values = Arrays.asList(first, second, third, fourth);

        service.applyFocusFloor(values);

        assertEquals(3, values.stream().filter(item -> item.getStage() == InvestmentObservationStage.FOCUS).count());
        assertTrue(first.isEvidenceInsufficient());
        assertTrue(second.isEvidenceInsufficient());
        assertTrue(third.isEvidenceInsufficient());
        assertEquals(InvestmentObservationStage.TRACKING, fourth.getStage());
    }

    private RadarEvent event(int hotspot, int confidence, int sources, String lifecycle) {
        RadarEvent value = new RadarEvent();
        value.setId((long) hotspot);
        value.setCanonicalTitle("资本开支与需求变化观察");
        value.setSummary("市场正在形成可跟踪的新变化");
        value.setCategoryCode("INDUSTRY");
        value.setHotspotScore(hotspot);
        value.setConfidenceScore(confidence);
        value.setSourceCount(sources);
        value.setEvidenceSourceCount(sources);
        value.setSignalCount(sources + 1);
        value.setHotspotLifecycleState(lifecycle);
        value.setFirstSeenAt(LocalDateTime.of(2026, 8, 19, 9, 0));
        value.setLastSeenAt(LocalDateTime.of(2026, 8, 20, 9, 0));
        value.setUpdatedAt(LocalDateTime.of(2026, 8, 20, 9, 0));
        value.setEvidenceFingerprint("event-" + hotspot);
        return value;
    }
}
