package com.finscope.service.research.quality;

import com.finscope.common.enums.research.*;
import com.finscope.domain.research.*;
import com.finscope.domain.research.quality.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ResearchQualityEngineTest {
    private final ResearchFactVerifier verifier = new ResearchFactVerifier();
    private final ResearchTaskContractBuilder builder = new ResearchTaskContractBuilder();

    @Test
    void freezesRelativeDatesAndDoesNotTurnQuestionNumbersIntoEvidence() {
        ResearchTaskContract contract = contract("长鑫科技今日大宗交易828.08万股，成交额4.3亿元，哪些事实已确认？");
        assertEquals("2026-11-06", contract.getDateFrom());
        assertTrue(contract.getRequiredFacts().containsAll(List.of(ResearchFactKind.AMOUNT, ResearchFactKind.PRICE)));
        ResearchQualityAssessment result = assess(contract, List.of());
        assertEquals(0, result.getFacts().size());
        assertEquals(0D, result.getTaskCoverage());
        assertFalse(result.complete());
    }

    @Test
    void validatesExactQuotesAndKeepsOriginalOffsetsAndUnits() {
        String text = "2026年11月6日，长鑫科技大宗交易成交828.08万股，成交额4.3亿元，成交均价51.96元。";
        ResearchQualityAssessment result = assess(contract("长鑫科技今日大宗交易"), List.of(doc("a", "a.cn", text)));
        assertEquals(1D, result.getTaskCoverage());
        assertTrue(result.getFacts().stream().anyMatch(f -> ResearchFactKind.PRICE == f.getKind() && "51.96".equals(f.getValue())));
        for (StructuredResearchFact fact : result.getFacts()) {
            assertTrue(fact.isVerified());
            assertEquals(fact.getQuote(), text.substring(fact.getQuoteStart(), fact.getQuoteEnd()));
        }
    }

    @Test
    void refusesFabricatedQuotesAndNumbersFromModel() {
        ResearchTaskContract contract = contract("长鑫科技大宗交易");
        ResearchEvidenceDocument doc = doc("a", "a.cn", "长鑫科技大宗交易成交额4.3亿元。");
        StructuredResearchFact candidate = new StructuredResearchFact();
        candidate.setQuote("长鑫科技大宗交易成交额4.3亿元。");
        candidate.setKind(ResearchFactKind.AMOUNT);
        candidate.setValue("8.6");
        candidate.setUnit("亿元");
        assertTrue(verifier.verify(contract, doc, candidate).getIssues().contains(ResearchEvidenceIssue.NUMBER_MISMATCH));
        candidate.setQuote("长鑫科技大宗交易成交额8.6亿元。");
        assertTrue(verifier.verify(contract, doc, candidate).getIssues().contains(ResearchEvidenceIssue.QUOTE_NOT_FOUND));
        assertFalse(candidate.isVerified());
    }

    @Test
    void rejectsWrongEntityEvenWithExpectedNumbersAndEvent() {
        ResearchQualityAssessment result = assess(contract("长鑫科技今日大宗交易"),
                List.of(doc("a", "a.cn", "2026年11月6日，兆易创新大宗交易成交828.08万股，成交额4.3亿元。")));
        assertEquals(0D, result.getTaskCoverage());
        assertFalse(result.getEvidence().get(0).isAccepted());
        assertTrue(result.getFeedback().stream().anyMatch(s -> s.contains("主体不符")));
    }

    @Test
    void rejectsOtherCompanyActionEvenWhenTargetSharesTheSentence() {
        ResearchRun run = new ResearchRun();
        run.setId(1L);
        run.setRunDate(LocalDate.of(2026, 11, 6));
        ResearchThesis thesis = new ResearchThesis();
        thesis.setSubjectName("微软");
        thesis.setQuestion("微软今日涨超5%是否确认？");
        ResearchQualityAssessment result = assess(builder.build(run, thesis), List.of(
                doc("a", "a.cn", "2026年11月6日，微软发布公告，特斯拉股价上涨6%。")));
        assertEquals(0D, result.getTaskCoverage());
    }

    @Test
    void cleanedFactsDoNotReintroduceNavigationIntoReportQuotes() {
        String raw = "移动版 网页版) 首页 新闻 视频 投资 基金 服务 您当前的位置：证券时报 > 公司 > 正文 长鑫科技大宗交易成交额4.3亿元。";
        ResearchQualityAssessment result = assess(contract("长鑫科技大宗交易"), List.of(doc("a", "a.cn", raw)));
        assertTrue(result.getFacts().stream().anyMatch(StructuredResearchFact::isVerified));
        assertTrue(result.getFacts().stream().filter(StructuredResearchFact::isVerified)
                .allMatch(fact -> !fact.getQuote().contains("首页")));
    }

    @Test
    void publicationDateCannotRepairAnOldOrUndatedEvent() {
        ResearchEvidenceDocument old = doc("a", "a.cn", "2026年8月6日，长鑫科技大宗交易成交额4.3亿元。");
        old.setPublishedDate("2026-11-06");
        ResearchQualityAssessment result = assess(contract("长鑫科技今日大宗交易"), List.of(old));
        assertEquals(0D, result.getTaskCoverage());
        assertTrue(result.getFacts().stream().allMatch(f -> f.getIssues().contains(ResearchEvidenceIssue.DATE_MISMATCH)));
        ResearchEvidenceDocument undated = doc("b", "b.cn", "长鑫科技大宗交易成交额4.3亿元。");
        undated.setPublishedDate("2026-11-06");
        assertTrue(assess(contract("长鑫科技今日大宗交易"), List.of(undated)).getFacts().stream()
                .allMatch(f -> f.getIssues().contains(ResearchEvidenceIssue.DATE_MISSING)));
    }

    @Test
    void publicationMetadataInBodyCannotSupplyEventDate() {
        ResearchEvidenceDocument doc = doc("a", "a.cn", "发布时间：2026年11月6日\n长鑫科技大宗交易成交额4.3亿元。");
        ResearchQualityAssessment result = assess(contract("长鑫科技今日大宗交易"), List.of(doc));
        assertEquals(0D, result.getTaskCoverage());
    }

    @Test
    void excludesEventsAfterResearchCutoffEvenWithoutAnExplicitTimeWindow() {
        ResearchQualityAssessment result = assess(contract("长鑫科技大宗交易"), List.of(
                doc("a", "a.cn", "2026年11月7日，长鑫科技大宗交易成交额4.3亿元。")));
        assertEquals(0D, result.getTaskCoverage());
        assertTrue(result.getFacts().stream().allMatch(f -> f.getIssues().contains(ResearchEvidenceIssue.DATE_MISMATCH)));
    }

    @Test
    void doesNotTreatSingleTradeAsConflictingWithDailyTotal() {
        ResearchQualityAssessment result = assess(contract("长鑫科技大宗交易"), List.of(
                doc("a", "a.cn", "2026年11月6日，长鑫科技大宗交易单笔成交额1亿元。"),
                doc("b", "b.cn", "2026年11月6日，长鑫科技大宗交易当日累计成交额4.3亿元。")));
        assertTrue(result.getConflicts().isEmpty());
        assertTrue(result.getFacts().stream().anyMatch(f -> f.getScope().contains("SINGLE_TRADE")));
        assertTrue(result.getFacts().stream().anyMatch(f -> f.getScope().contains("DAILY_TOTAL")));
    }

    @Test
    void distinguishesPriceChangeBoundsFromExactNumbersAndChecksAllPairs() {
        ResearchRun run = new ResearchRun();
        run.setId(1L);
        run.setRunDate(LocalDate.of(2026, 11, 6));
        ResearchThesis thesis = new ResearchThesis();
        thesis.setSubjectName("微软");
        thesis.setQuestion("微软今日股价上涨是否确认？");
        ResearchTaskContract contract = builder.build(run, thesis);
        ResearchEvidenceDocument bound = doc("a", "a.cn", "2026-11-06，微软股价涨超5%。");
        ResearchEvidenceDocument exact = doc("b", "b.cn", "2026-11-06，微软股价上涨6%。");
        ResearchQualityAssessment compatible = assess(contract, List.of(bound, exact));
        assertEquals(1D, compatible.getTaskCoverage());
        assertTrue(compatible.getConflicts().isEmpty());
        assertTrue(compatible.getFacts().stream().anyMatch(f -> f.getValueRelation() == ResearchValueRelation.GREATER_THAN));
        ResearchEvidenceDocument different = doc("c", "c.cn", "2026-11-06，微软股价上涨7%。");
        assertFalse(assess(contract, List.of(bound, exact, different)).getConflicts().isEmpty());
        ResearchEvidenceDocument inconsistent = doc("d", "d.cn", "2026-11-06，微软股价上涨4%。");
        assertFalse(assess(contract, List.of(bound, inconsistent)).getConflicts().isEmpty());
    }

    @Test
    void snippetsAreSearchLeadsAndCannotSatisfyFactCoverage() {
        ResearchEvidenceDocument snippet = doc("a", "a.cn", "2026年11月6日，长鑫科技大宗交易成交额4.3亿元。");
        snippet.setSnippetOnly(true);
        ResearchQualityAssessment result = assess(contract("长鑫科技今日大宗交易"), List.of(snippet));
        assertEquals(0D, result.getTaskCoverage());
        assertEquals(0, result.getIndependentSourceCount());
        assertTrue(result.getFeedback().stream().anyMatch(s -> s.contains("全文")));
    }

    @Test
    void mergesSyndicatedSourcesAcrossDifferentDomainsTransitively() {
        String quote = "来源：界面新闻\n2026年11月6日，长鑫科技大宗交易成交828.08万股，成交额4.3亿元，成交均价51.96元。";
        ResearchQualityAssessment result = assess(contract("长鑫科技今日大宗交易"), List.of(
                doc("a", "a.cn", quote), doc("b", "b.cn", quote), doc("c", "c.cn", quote + "报道完。")));
        assertEquals(1, result.getIndependentSourceCount());
        assertEquals(1, result.getEvidence().stream().map(ResearchEvidenceReview::getSourceGroup).distinct().count());
        assertFalse(result.complete());
    }

    @Test
    void detectsSameDateSameMetricConflictsButNormalizesUnits() {
        ResearchTaskContract contract = contract("长鑫科技大宗交易");
        ResearchEvidenceDocument first = doc("a", "a.cn", "2026年11月6日，长鑫科技大宗交易成交额4.3亿元。");
        ResearchEvidenceDocument equal = doc("b", "b.cn", "2026年11月6日，长鑫科技大宗交易成交金额43000万元。");
        assertTrue(assess(contract, List.of(first, equal)).getConflicts().isEmpty());
        ResearchEvidenceDocument conflict = doc("c", "c.cn", "2026年11月6日，长鑫科技大宗交易成交额5.3亿元。");
        ResearchQualityAssessment result = assess(contract, List.of(first, conflict));
        assertFalse(result.getConflicts().isEmpty());
        assertFalse(result.complete());
        ResearchEvidenceDocument later = doc("d", "d.cn", "2026年11月5日，长鑫科技大宗交易成交额5.3亿元。");
        assertTrue(assess(contract, List.of(first, later)).getConflicts().isEmpty());
    }

    @Test
    void modelCannotInventDateOrScopeForAnUndatedQuote() {
        StructuredResearchFact fact = new StructuredResearchFact();
        fact.setKind(ResearchFactKind.AMOUNT);
        fact.setQuote("长鑫科技大宗交易成交额4.3亿元。");
        fact.setValue("4.3");
        fact.setUnit("亿元");
        fact.setEventDate("2026-11-06");
        fact.setScope("2026年全年");
        verifier.verify(contract("长鑫科技今日大宗交易"), doc("a", "a.cn", fact.getQuote()), fact);
        assertNull(fact.getEventDate());
        assertNull(fact.getScope());
        assertFalse(fact.isVerified());
    }

    @Test
    void coverageRequiresAllRequestedResearchDimensions() {
        ResearchQualityAssessment result = assess(contract("按空间、盈利模式、竞争、治理、估值和反方验证研究长鑫科技"),
                List.of(doc("a", "a.cn", "长鑫科技主营业务为存储芯片。长鑫科技竞争对手增加。")));
        assertTrue(result.getCoveredFacts().contains(ResearchFactKind.PROFIT_MODEL));
        assertTrue(result.getMissingConditions().stream().anyMatch(s -> s.contains("估值")));
        assertTrue(result.getTaskCoverage() < 1D);
    }

    @Test
    void rejectsFuturePredictionsAndNavigationAsVerifiedFacts() {
        ResearchQualityAssessment result = assess(contract("长鑫科技大宗交易"), List.of(
                doc("a", "a.cn", "长鑫科技预计大宗交易成交额4.3亿元。"),
                doc("b", "b.cn", "首页 快讯 新闻 登录 注册 手机版 网页版")));
        assertEquals(0D, result.getTaskCoverage());
        assertTrue(result.getFacts().stream().noneMatch(StructuredResearchFact::isVerified));
    }

    @Test
    void qualityImprovesAfterRejectedEvidenceIsReplaced() {
        ResearchTaskContract contract = contract("长鑫科技今日大宗交易");
        ResearchQualityAssessment before = assess(contract, List.of(doc("a", "a.cn", "兆易创新今日大宗交易成交额4.3亿元。")));
        ResearchQualityAssessment after = assess(contract, List.of(doc("b", "b.cn",
                "2026年11月6日，长鑫科技大宗交易成交828.08万股，成交额4.3亿元，成交均价51.96元。")));
        assertTrue(after.getTaskCoverage() > before.getTaskCoverage());
        assertFalse(after.getFacts().isEmpty());
        assertFalse(before.getRecoveryQuery().isBlank());
    }

    private ResearchQualityAssessment assess(ResearchTaskContract contract, List<ResearchEvidenceDocument> docs) {
        ResearchQualityEngine engine = new ResearchQualityEngine();
        ReflectionTestUtils.setField(engine, "verifier", verifier);
        ReflectionTestUtils.setField(engine, "lineage", new ResearchSourceLineage());
        return engine.assess(contract, docs, Collections.emptyList());
    }

    private ResearchTaskContract contract(String question) {
        ResearchRun run = new ResearchRun();
        run.setId(1L);
        run.setRunDate(LocalDate.of(2026, 11, 6));
        ResearchThesis thesis = new ResearchThesis();
        thesis.setSubjectName("长鑫科技");
        thesis.setQuestion(question);
        return builder.build(run, thesis);
    }

    private ResearchEvidenceDocument doc(String ref, String domain, String text) {
        ResearchEvidenceDocument doc = new ResearchEvidenceDocument();
        doc.setReference(ref);
        doc.setText(text);
        doc.setUrl("https://" + domain + "/disclosure");
        doc.setSelected(true);
        return doc;
    }
}
