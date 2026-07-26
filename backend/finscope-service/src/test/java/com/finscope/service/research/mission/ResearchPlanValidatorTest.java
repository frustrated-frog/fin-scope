package com.finscope.service.research.mission;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchPlanValidatorTest {
    private ResearchPlanValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ResearchPlanValidator(new ResearchToolRegistry());
    }

    @Test
    void acceptsAValidBoundedDagAndReturnsTopologicalOrder() {
        ResearchMissionDraft validated = validator.validate(validDraft());

        assertEquals(6, validated.getTasks().size());
        assertEquals("baseline_scan", validated.getTasks().get(0).getTaskKey());
        assertEquals("synthesize_report", validated.getTasks().get(5).getTaskKey());
        assertEquals(4, new ResearchToolRegistry().list().size());
        assertTrue(new ResearchToolRegistry().contains("public_news_search"));
    }

    @Test
    void rejectsUnknownToolCycleDuplicateKeyAndMissingCounterIntent() {
        ResearchMissionDraft unknownTool = validDraft();
        unknownTool.getTasks().get(1).setToolCode("shell");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(unknownTool));

        ResearchMissionDraft cycle = validDraft();
        cycle.getTasks().get(0).setDependencies(Arrays.asList("synthesize_report"));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(cycle));

        ResearchMissionDraft duplicate = validDraft();
        duplicate.getTasks().get(1).setTaskKey("baseline_scan");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(duplicate));

        ResearchMissionDraft noCounter = validDraft();
        noCounter.getTasks().remove(2);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(noCounter));
    }

    @Test
    void rejectsUnsafeOrUnboundedModelOutput() {
        ResearchMissionDraft protocolQuery = validDraft();
        protocolQuery.getTasks().get(1).setQueryText("https://example.com/private");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(protocolQuery));

        ResearchMissionDraft tooLong = validDraft();
        tooLong.getTasks().get(1).setQueryText(repeat("研究", 100));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(tooLong));

        ResearchMissionDraft tooManySearches = validDraft();
        for (int index = 0; index < 3; index++) {
            ResearchMissionTaskDraft task = task("search_extra_" + index, "补充搜索" + index,
                    "public_news_search", "PRIMARY", Arrays.asList("baseline_scan"));
            tooManySearches.getTasks().add(task);
        }
        assertThrows(IllegalArgumentException.class, () -> validator.validate(tooManySearches));
    }

    private ResearchMissionDraft validDraft() {
        ResearchMissionDraft draft = new ResearchMissionDraft();
        draft.setScopeSummary("聚焦需求、供给、兑现与反方风险");
        draft.setSuccessCriteria(Arrays.asList("至少两个独立来源", "同时覆盖支持与反方证据"));
        List<ResearchMissionTaskDraft> tasks = new ArrayList<ResearchMissionTaskDraft>();
        tasks.add(task("baseline_scan", "基线扫描", "source_scan", "BASELINE",
                Collections.<String>emptyList()));
        tasks.add(task("search_support", "支持证据搜索", "public_news_search", "SUPPORT",
                Arrays.asList("baseline_scan")));
        tasks.add(task("search_counter", "反方证据搜索", "public_news_search", "COUNTER",
                Arrays.asList("baseline_scan")));
        tasks.add(task("search_primary", "一手证据搜索", "public_news_search", "PRIMARY",
                Arrays.asList("baseline_scan")));
        tasks.add(task("assess_evidence", "证据判断", "evidence_assess", "ASSESS",
                Arrays.asList("search_support", "search_counter", "search_primary")));
        tasks.add(task("synthesize_report", "报告合成", "report_synthesis", "SYNTHESIS",
                Arrays.asList("assess_evidence")));
        draft.setTasks(tasks);
        return draft;
    }

    private ResearchMissionTaskDraft task(String key,
                                          String title,
                                          String toolCode,
                                          String intent,
                                          List<String> dependencies) {
        ResearchMissionTaskDraft task = new ResearchMissionTaskDraft();
        task.setTaskKey(key);
        task.setTitle(title);
        task.setQuestion(title + "要回答什么？");
        task.setTaskType("SEARCH");
        task.setToolCode(toolCode);
        task.setIntent(intent);
        task.setDependencies(dependencies);
        task.setParallelGroup("evidence_search");
        task.setQueryText("public_news_search".equals(toolCode) ? title + " 最新事实" : null);
        task.setRationale("补齐研究合同要求的证据");
        task.setExpectedEvidence("公开可引用资料");
        return task;
    }

    private String repeat(String value, int count) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }
}
