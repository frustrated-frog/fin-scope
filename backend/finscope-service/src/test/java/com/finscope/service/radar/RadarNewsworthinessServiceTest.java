package com.finscope.service.radar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.radar.NewsworthinessAssessment;
import com.finscope.domain.radar.NewswireRankingPolicy;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarSignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RadarNewsworthinessServiceTest {
    private final LocalDateTime now = LocalDateTime.parse("2026-11-17T10:00:00");
    private RadarNewsworthinessService service;
    private final RadarHotspotScoreService heat = new RadarHotspotScoreService();

    @BeforeEach
    void setup() {
        service = new RadarNewsworthinessService();
        ReflectionTestUtils.setField(service, "properties", new NewswireRankingProperties());
    }

    @Test
    void importantSingleSourceOutranksRoutinePersonalNewsAndDoesNotNeedMultipleSources() {
        var policy = assess(List.of(signal("PBOC", "央行宣布降准0.5%", "央行宣布降准0.5%，预计释放长期流动性一万亿元。", now)), null, now);
        var routine = assess(List.of(signal("CLS", "宁德时代董事会会议通知", "宁德时代董事会会议通知，公司拟于下周召开董事会。", now)), null, now);
        assertTrue(policy.getImportance() >= 90);
        assertTrue(policy.getPublicScore() > routine.getPublicScore() + 12);
        assertTrue(policy.getPublicScore() >= 70);
    }

    @Test
    void unchangedCopyWithNewTimestampCannotCreateNewFactsOrRefreshTheClock() {
        RadarSignal original = signal("CLS", "宁德时代发布新电池", "宁德时代发布新电池，能量密度提升20%。", now.minusHours(12));
        var first = assess(List.of(original), null, now.minusMinutes(30));
        List<RadarSignal> copies = new ArrayList<>(); copies.add(original);
        for (int index = 0; index < 20; index++) {
            copies.add(signal("COPY_" + index, original.getTitle(), original.getContent(), now));
        }
        var next = assess(copies, first, now);
        assertEquals(0, next.getNewFactCount());
        assertEquals(first.getMeaningfulChangeAt(), next.getMeaningfulChangeAt());
        assertTrue(next.getPublicScore() <= first.getPublicScore());
    }

    @Test
    void sameProviderFactualDevelopmentCanRegainPriorityWithoutAddingIndependentSources() {
        RadarSignal original = signal("CLS", "宁德时代发布产能计划", "宁德时代发布产能计划，新增产能100GWh。", now.minusHours(6));
        var first = assess(List.of(original), null, now.minusMinutes(30));
        RadarSignal followUp = signal("CLS", "宁德时代宣布投产进展", "宁德时代发布产能计划进展，新增产能200GWh。", now);
        var next = assess(List.of(original, followUp), first, now);
        assertEquals(1, next.getNewFactCount());
        assertEquals(now, next.getMeaningfulChangeAt());
        assertTrue(next.getPublicScore() > first.getPublicScore());
    }

    @Test
    void repeatedEvaluationIsIdempotentAndLateBackfillDoesNotBecomeADevelopment() {
        var original = signal("CLS", "宁德时代发布产能计划", "宁德时代发布产能计划，新增产能100GWh。", now.minusHours(1));
        var first = assess(List.of(original), null, now);
        var late = signal("THS", "宁德时代宣布投产进展", "宁德时代发布产能计划进展，新增产能200GWh。", now.minusHours(3));
        var second = assess(List.of(original, late), first, now.plusMinutes(5));
        var third = assess(List.of(original, late), second, now.plusMinutes(5));
        assertEquals(0, second.getNewFactCount());
        assertEquals(first.getMeaningfulChangeAt(), second.getMeaningfulChangeAt());
        assertEquals(second, third);
    }

    @Test
    void contentWithUnknownOrFuturePublicationTimeCannotGetARecencyBonus() {
        var missing = signal("CLS", "央行宣布降准0.5%", "央行宣布降准0.5%，预计释放长期流动性一万亿元。", null);
        var future = signal("PBOC", missing.getTitle(), missing.getContent(), now.plusHours(1));
        assertEquals(0, assess(List.of(missing), null, now).getPublicScore());
        assertEquals(0, assess(List.of(future), null, now).getPublicScore());
        assertEquals(0, assess(List.of(), null, now).getPublicScore());
    }

    @Test
    void rumorCannotReceiveTheImportanceOfAnImplementedPolicy() {
        var implemented = assess(List.of(signal("PBOC", "央行宣布降准0.5%", "央行宣布降准0.5%，预计释放长期流动性一万亿元。", now)), null, now);
        var rumor = assess(List.of(signal("MEDIA", "传闻央行可能会降准0.5%", "传闻央行可能会降准0.5%，相关消息仍在流传。", now)), null, now);
        assertTrue(implemented.getPublicScore() > rumor.getPublicScore());
        assertTrue(rumor.getImportance() <= 40);
    }

    @Test
    void factsSurviveSerializationAndRemainBoundedInTheCurrentEventOnly() throws Exception {
        List<RadarSignal> values = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            values.add(signal("CLS", "宁德时代发布产能计划", "宁德时代发布产能计划，新增产能" + index + "GWh。", now));
        }
        var result = assess(values, null, now);
        assertTrue(result.getKnownFacts().size() <= 64);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        assertEquals(result, mapper.readValue(mapper.writeValueAsBytes(result), NewsworthinessAssessment.class));
        assertEquals(0, assess(values, result, now.plusMinutes(5)).getNewFactCount());
    }

    @Test
    void personalBoostIsBoundedAndConfidenceIsNotCountedAgain() {
        RadarPriorityService priority = new RadarPriorityService();
        RadarEvent event = new RadarEvent(); event.setCanonicalTitle("央行宣布降准");
        var value = new NewsworthinessAssessment(); value.setPublicScore(85); event.setNewsworthiness(value);
        var low = priority.score(event, Collections.emptyList(), Collections.emptyList(), now, 30, 20);
        var high = priority.score(event, Collections.emptyList(), Collections.emptyList(), now, 90, 100);
        assertEquals(85, low.getTotalScore());
        assertEquals(low.getTotalScore(), high.getTotalScore());
    }

    @Test
    void diversityPenaltyIsSoftAndDeterministicAndPreservesEveryCandidate() {
        var a = event(1L, "company:A", 90); var b = event(2L, "company:A", 89); var c = event(3L, "company:B", 85);
        assertEquals(List.of(a, c, b), NewswireRankingPolicy.rank(List.of(b, c, a), false));
        assertEquals(List.of(a, c, b), NewswireRankingPolicy.rank(List.of(a, b, c), false));
        c.getNewsworthiness().setPublicScore(30);
        assertEquals(List.of(a, b, c), NewswireRankingPolicy.rank(List.of(c, b, a), false));
    }

    @Test
    void realDisclosurePatternsDoNotOccupyTheBoardAheadOfEarningsAndPolicy() {
        List<String> routineTitles = List.of("预告：商务部定于周四下午举行新闻发布会", "康辰药业:关于召开2026年第三季度业绩说明会的公告",
                "凌钢股份:关于2026年第三季度业绩说明会召开情况的公告", "吉视传媒:2026年第二次临时股东会决议公告",
                "大地熊:投资者关系活动记录表(2026-005)", "翔楼新材:第四届董事会第十一次会议决议公告",
                "芯碁微装:北京德恒律师事务所关于临时股东会见证法律意见");
        var earnings = assess(List.of(signal("CLS", "京东发布2026年三季度业绩 服务收入764亿元占比24.7%创新高",
                "京东发布2026年三季度业绩，服务收入764亿元，占比24.7%创新高。", now.minusHours(2))), null, now);
        for (String title : routineTitles) {
            var disclosure = assess(List.of(signal("SSE", title, title + "，公司根据有关规定发布本次公告。", now)), null, now);
            assertTrue(disclosure.getImportance() <= 10, title);
            assertTrue(earnings.getPublicScore() > disclosure.getPublicScore(), title);
        }
    }

    @Test
    void invalidWeightsCannotSilentlyChangeScoreScale() {
        var configuration = new NewswireRankingProperties(); configuration.validate();
        configuration.setHeatWeight(-0.2);
        assertThrows(IllegalArgumentException.class, configuration::validate);
        configuration.setHeatWeight(Double.NaN);
        assertThrows(IllegalArgumentException.class, configuration::validate);
    }

    private NewsworthinessAssessment assess(List<RadarSignal> signals, NewsworthinessAssessment previous, LocalDateTime at) {
        var score = heat.score(signals, at);
        return service.assess(score.getSourceAnalysis(), score.getTotalScore(), previous, at);
    }

    private RadarSignal signal(String provider, String title, String content, LocalDateTime at) {
        RadarSignal value = new RadarSignal(); value.setProviderCode(provider); value.setSourceName(provider);
        value.setSourceTier("PBOC".equals(provider) ? "OFFICIAL" : "TIER_1");
        value.setTitle(title); value.setContent(content); value.setPublishedAt(at); value.setCategoryCode("COMPANY");
        return value;
    }

    private RadarEvent event(Long id, String subject, int score) {
        RadarEvent event = new RadarEvent(); event.setId(id); event.setEventKey("event:" + id);
        NewsworthinessAssessment value = new NewsworthinessAssessment(); value.setPublicScore(score); value.setDiversityKey(subject);
        event.setNewsworthiness(value); event.setPriorityScore(score); return event;
    }
}
