package com.finscope.service.radar;

import com.finscope.domain.radar.RadarSignal;
import com.finscope.service.dedupe.FingerprintService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadarSourceIndependenceServiceTest {
    private final RadarTextAnalyzer analyzer = new RadarTextAnalyzer(new FingerprintService());
    private final RadarSourceIndependenceService service = new RadarSourceIndependenceService(analyzer);

    @Test
    void countsCopiedReportsAsOneEffectiveConfirmation() {
        RadarSignal original = signal("CLS", "财联社", "宁德时代发布新一代电池", "宁德时代发布新一代电池，能量密度提升20%");
        RadarSignal copied = signal("AGGREGATOR", "资讯聚合", "宁德时代发布新一代电池", "宁德时代发布新一代电池，能量密度提升20%");

        RadarSourceIndependenceService.Analysis result = service.analyze(Arrays.asList(original, copied));

        assertEquals(1, result.getIndependentSourceCount());
        assertEquals(1, result.getRepostClusterCount());
        assertTrue(result.getRepostConcentration() > 0.4D);
    }

    @Test
    void countsDifferentReportingFromAnotherSourceAsIndependentConfirmation() {
        RadarSignal original = signal("CLS", "财联社", "宁德时代发布新一代电池", "宁德时代发布新一代电池，能量密度提升20%");
        RadarSignal confirmation = signal("SSE", "上交所", "300750 公告新型电池量产计划", "公司公告披露新型电池量产时间和产能");
        confirmation.setSourceTier("OFFICIAL");

        RadarSourceIndependenceService.Analysis result = service.analyze(Arrays.asList(original, confirmation));

        assertEquals(2, result.getIndependentSourceCount());
        assertEquals(2, result.getRepostClusterCount());
        assertTrue(result.hasOfficialSource());
        assertTrue(result.getAuthorityScore() >= 0.85D);
    }

    @Test
    void collapsesChannelsOwnedByTheSameProviderGroup() {
        RadarSignal app = signal("CLS", "财联社APP", "宁德时代电池产能提升", "财联社获悉宁德时代电池产能提升");
        RadarSignal telegraph = signal("CLS_TELEGRAPH", "财联社电报", "宁德时代电池项目扩产", "电报称宁德时代电池项目扩产");

        RadarSourceIndependenceService.Analysis result = service.analyze(Arrays.asList(app, telegraph));

        assertEquals(1, result.getIndependentSourceCount());
        assertEquals("CLS", result.getObservations().get(0).getSourceGroup());
        assertEquals("CLS", result.getObservations().get(1).getSourceGroup());
    }

    private RadarSignal signal(String provider, String source, String title, String content) {
        RadarSignal signal = new RadarSignal();
        signal.setProviderCode(provider);
        signal.setSourceName(source);
        signal.setSourceTier("TIER_2");
        signal.setTitle(title);
        signal.setContent(content);
        signal.setCategoryCode("COMPANY");
        signal.setPublishedAt(LocalDateTime.of(2026, 8, 13, 9, 0));
        return signal;
    }
}
