package com.finscope.service.radar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.radar.NewswireRankingPolicy;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarSignal;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** 对固定时点、固定事件集合离线重放；不连接 Redis、数据库、外部来源或模型。 */
class NewswireCorpusEvaluationTest {
    @Test
    void comparesPublicRankingOnAFrozenLocalCorpus() throws Exception {
        String file = System.getProperty("newswire.corpus");
        assumeTrue(file != null && !file.isBlank(), "可通过 -Dnewswire.corpus=/绝对路径/corpus.json 启用真实语料回放");
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        Corpus corpus = mapper.readValue(Path.of(file).toFile(), Corpus.class);
        assertNotNull(corpus.getCapturedAt());
        var heat = new RadarHotspotScoreService();
        var evaluator = new RadarNewsworthinessService();
        ReflectionTestUtils.setField(evaluator, "properties", new NewswireRankingProperties());
        List<RadarEvent> events = new ArrayList<>();
        long started = System.nanoTime();
        int signalCount = 0;
        for (Sample sample : corpus.getEvents()) {
            var event = sample.getEvent();
            var score = heat.score(sample.getSignals(), corpus.getCapturedAt());
            var assessment = evaluator.assess(score.getSourceAnalysis(), score.getTotalScore(), null, corpus.getCapturedAt());
            var repeated = evaluator.assess(score.getSourceAnalysis(), score.getTotalScore(), assessment, corpus.getCapturedAt());
            assertEquals(assessment.getPublicScore(), repeated.getPublicScore(), event.getCanonicalTitle());
            assertEquals(0, repeated.getNewFactCount(), event.getCanonicalTitle());
            assertTrue(assessment.getPublicScore() >= 0 && assessment.getPublicScore() <= 100);
            event.setHotspotScore(score.getTotalScore()); event.setNewsworthiness(assessment);
            events.add(event); signalCount += sample.getSignals().size();
        }
        long evaluationMs = (System.nanoTime() - started) / 1_000_000;
        List<RadarEvent> old = new ArrayList<>(events);
        old.sort(Comparator.comparingInt(RadarEvent::getHotspotScore).reversed()
                .thenComparing(RadarEvent::getId, Comparator.reverseOrder()));
        long sortStarted = System.nanoTime();
        List<RadarEvent> ranked = NewswireRankingPolicy.rank(events, false);
        long selectionMs = (System.nanoTime() - sortStarted) / 1_000_000;
        assertEquals(new HashSet<>(events.stream().map(RadarEvent::getId).toList()), new HashSet<>(ranked.stream().map(RadarEvent::getId).toList()));
        StringBuilder report = new StringBuilder("# News Wire 固定语料排序对比\n\n固定时点：" + corpus.getCapturedAt()
                + "（北京时间）。事件 " + events.size() + "；关联报道 " + signalCount
                + "；特征、热度、两次评价与一致性检查共 " + evaluationMs + " ms；新排序 " + selectionMs
                + " ms。\n\n同一时点重算 V3 热度，对比新公共资讯价值排序；事件集合与聚类保持固定。分数语义不同，排名变化不代表准确率提升。\n");
        append(report, "原公共热度排序", old);
        append(report, "新公共资讯价值排序", ranked);
        Path output = Path.of(System.getProperty("newswire.report", file + ".report.md"));
        Files.writeString(output, report);
    }

    private void append(StringBuilder report, String label, List<RadarEvent> events) {
        report.append("\n## ").append(label).append("\n\n|排名|标题|热度|公共价值|重要性|新意|\n|---|---|---|---|---|---|\n");
        int index = 0;
        for (RadarEvent event : events.stream().limit(20).toList()) {
            var value = event.getNewsworthiness();
            report.append("|").append(++index).append("|").append(event.getCanonicalTitle().replace("|", "/").replace("\n", " "))
                    .append("|").append(event.getHotspotScore()).append("|").append(value.getPublicScore())
                    .append("|").append(value.getImportance()).append("|").append(value.getNovelty()).append("|\n");
        }
    }

    @Data
    private static class Corpus {
        private LocalDateTime capturedAt;
        private List<Sample> events;
    }

    @Data
    private static class Sample {
        private RadarEvent event;
        private List<RadarSignal> signals;
    }
}
