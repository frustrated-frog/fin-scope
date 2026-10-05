package com.finscope.service.financials;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.financials.FinancialInterpretationChapter;
import com.finscope.domain.financials.FinancialEvidence;
import com.finscope.domain.financials.FinancialInterpretation;
import com.finscope.domain.financials.FinancialInterpretationSection;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FinancialInterpretationReportTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void acceptsFullReportAndAddsServerOwnedTeachingWithoutInventingBusinessMaterials() {
        FinancialInterpretation.Result result = accept(valid(), packet());
        assertEquals(10, result.getSections().size());
        assertEquals("financial-interpret-v5", result.getReportVersion());
        assertEquals("公司与商业模式", result.getSections().get(0).getTitle());
        assertTrue(result.getSections().get(0).getLearningExplanation().contains("商业模式"));
        assertEquals("INSUFFICIENT_EVIDENCE", result.getSections().get(0).getAssessment());
        assertTrue(result.getSections().get(0).getFacts().isEmpty());
    }

    @Test
    void rejectsNumbersThatExistInThePacketButNotInTheClaimsOwnReferences() {
        FinancialInterpretation.Result result = valid();
        result.getExecutiveSummary().get(0).setClaim("营业收入为99.9");
        reject(result, "数字未被本条引用支持");
    }

    @Test
    void rejectsCausesLabeledAsFactsAndOverconfidentInference() {
        FinancialInterpretation.Result result = valid();
        result.getExecutiveSummary().get(0).setClaim("营业收入增长得益于产品涨价");
        reject(result, "不能标为事实");
        result = valid();
        result.getSections().get(1).getAnalysis().get(0).setConfidence("HIGH");
        reject(result, "原因推断最高为中置信度");
    }

    @Test
    void requiresCompleteChaptersAndCounterEvidence() {
        FinancialInterpretation.Result result = valid();
        result.getSections().remove(9);
        reject(result, "十个研究章节");
        result = valid();
        result.getSections().get(1).getCounterEvidence().clear();
        reject(result, "替代解释与后续验证");
    }

    @Test
    void refusesBusinessClaimsWhenOriginalMaterialsAreMissing() {
        FinancialInterpretation.Result result = valid();
        result.getSections().get(0).setAssessment("POSITIVE");
        reject(result, "缺少本章材料");
    }

    @Test
    void rejectsSinglePeriodContinuousTrendsAndMetricsOnlyCrossStatementClaims() {
        FinancialInterpretation.Result result = valid();
        result.getExecutiveSummary().get(0).setClaim("营业收入连续改善");
        reject(result, "多时点趋势证据");
        result = valid();
        result.getCrossStatementInsights().add(claim("利润与现金需联合核查", "INFERENCE", "M_REVENUE_YOY"));
        reject(result, "两个报表域");
    }

    @Test
    void handlesExplicitNullArraysAsRejectedOutputInsteadOfCrashing() {
        FinancialInterpretation.Result result = valid();
        result.getSections().get(1).setFacts(null);
        result.getSections().get(1).setRefs(null);
        reject(result, "facts 必须为数组");
    }

    @Test
    void acceptsDisplayRoundingAndSeparatorsOnlyWhenSupportedByOwnEvidence() {
        FinancialInterpretation.Result result = valid();
        result.getExecutiveSummary().get(0).setClaim("营业收入同比约12%");
        accept(result, packet());
        FinancialEvidencePacket packet = packet();
        packet.getEvidenceIndex().get("M_REVENUE_YOY").setValue("12345.6789");
        result.getExecutiveSummary().get(0).setClaim("营业收入为12,345.68");
        accept(result, packet);
    }

    @Test
    void distinguishesUnconfirmedTrendsAndFutureVerificationFromAssertions() {
        FinancialInterpretation.Result result = valid();
        result.getExecutiveSummary().get(0).setClaimType("INFERENCE");
        result.getExecutiveSummary().get(0).setClaim("现有两个时点不足以证明连续改善，后续观察是否逐季改善。");
        accept(result, packet());
        result.getExecutiveSummary().get(0).setClaim("两个时点不构成连续趋势，若后续经营现金流能持续改善，则需要更新判断。");
        accept(result, packet());
        result.getExecutiveSummary().get(0).setClaim("后续关注营业收入是否环比增长，不能视作相邻季度的已知变化。");
        accept(result, packet());
        result.getExecutiveSummary().get(0).setClaim("虽然无法证明连续改善，但营业收入持续改善。");
        reject(result, "多时点趋势证据");
        result = valid();
        result.getLimitations().add("公司收入为999");
        reject(result, "无引用限制段落不得补写数字");
    }

    @Test
    void rejectsCallingTwoSameQuarterYearOnYearPointsAdjacentQuarters() {
        FinancialInterpretation.Result result = valid();
        result.getSections().get(1).setSummary("仅有相邻两个季度时点，不能确认连续趋势。");
        reject(result, "上年同季间隔不是相邻季度");
        FinancialEvidencePacket packet = packet();
        FinancialEvidence evidence = new FinancialEvidence();
        evidence.setId("T_REVENUE_QUARTER");
        evidence.setType("TREND");
        evidence.setDetail("2025-12-31=100;2026-03-31=1200");
        packet.getEvidenceIndex().put(evidence.getId(), evidence);
        packet.getModelEvidence().add(evidence);
        result.getSections().get(1).setSummary("本期营业收入环比增长。");
        result.getSections().get(1).setRefs(List.of(evidence.getId()));
        accept(result, packet);
    }

    @Test
    void providesTenHonestChaptersEvenWhenTheModelIsUnavailable() {
        FinancialInterpretation value = new FinancialInterpretationFallbackBuilder().build(packet(), "LLM_TIMEOUT");
        assertEquals("FALLBACK", value.getStatus());
        assertEquals("INSUFFICIENT_EVIDENCE", value.getResult().getOperatingState());
        assertEquals(10, value.getResult().getSections().size());
        assertTrue(value.getResult().getSections().stream().allMatch(section -> "LOW".equals(section.getConfidence())));
        assertTrue(value.getResult().getSections().get(0).getFacts().isEmpty());
    }

    private FinancialInterpretation.Result accept(FinancialInterpretation.Result result, FinancialEvidencePacket packet) {
        FinancialInterpretationGate gate = new FinancialInterpretationGate();
        ReflectionTestUtils.setField(gate, "json", json);
        return gate.apply(json.valueToTree(result), packet);
    }

    private void reject(FinancialInterpretation.Result result, String message) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> accept(result, packet()));
        assertTrue(failure.getMessage().contains(message), failure.getMessage());
    }

    private FinancialEvidencePacket packet() {
        FinancialEvidencePacket packet = new FinancialEvidencePacket();
        packet.setPromptVersion(FinancialInterpretationReportFramework.VERSION);
        packet.setQualityCeiling("HIGH");
        packet.setAllowedNumbers(new LinkedHashSet<>(List.of("12.3", "99.9")));
        for (String id : List.of("M_REVENUE_YOY", "M_PROFIT", "M_FREE_CASH_FLOW", "M_TOTAL_ASSETS", "M_DEBT_TO_ASSETS", "M_CAPITAL_EXPENDITURE")) {
            FinancialEvidence evidence = new FinancialEvidence();
            evidence.setId(id);
            evidence.setType("METRIC");
            evidence.setLabel(id);
            evidence.setValue(id.equals("M_REVENUE_YOY") ? "12.3" : "99.9");
            packet.getModelEvidence().add(evidence);
            packet.getEvidence().add(evidence);
            packet.getEvidenceIndex().put(id, evidence);
        }
        return packet;
    }

    private FinancialInterpretation.Result valid() {
        FinancialEvidencePacket packet = packet();
        FinancialInterpretation.Result result = new FinancialInterpretation.Result();
        result.setOperatingState("STABLE");
        result.setConfidence("MEDIUM");
        result.setDisclaimer("仅用于研究，不构成投资建议。");
        result.getExecutiveSummary().add(claim("营业收入同比12.3%", "FACT", "M_REVENUE_YOY"));
        result.setSections(FinancialInterpretationReportFramework.plan(packet.getModelEvidence()));
        for (FinancialInterpretationSection section : result.getSections()) {
            section.setSummary("根据已有材料核查本期表现，缺失信息需要另行补齐。");
            if (section.getRefs().isEmpty()) {
                section.setAssessment("INSUFFICIENT_EVIDENCE");
                section.setConfidence("LOW");
                continue;
            }
            section.setAssessment("NEUTRAL");
            section.setConfidence("MEDIUM");
            String ref = section.getRefs().get(0);
            section.getFacts().add(claim("当前科目来自财报底稿", "FACT", ref));
            section.getAnalysis().add(claim("变化可能与经营投入有关，需进一步核查", "INFERENCE", ref));
            section.getCounterEvidence().add(claim("也可能受结算时点影响，不能确认原因", "INFERENCE", ref));
            section.getWatchpoints().add(claim("后续查看相关科目的变化和附注", "WATCHPOINT", ref));
        }
        return result;
    }

    private FinancialInterpretation.Claim claim(String text, String type, String ref) {
        FinancialInterpretation.Claim claim = new FinancialInterpretation.Claim();
        claim.setClaim(text);
        claim.setClaimType(type);
        claim.setConfidence("MEDIUM");
        claim.getRefs().add(ref);
        return claim;
    }
}
