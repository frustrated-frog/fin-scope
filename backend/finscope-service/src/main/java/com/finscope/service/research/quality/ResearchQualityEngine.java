package com.finscope.service.research.quality;

import com.finscope.common.enums.research.ResearchEvidenceIssue;
import com.finscope.common.enums.research.ResearchFactKind;
import com.finscope.common.util.WebContentText;
import com.finscope.domain.research.quality.*;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.util.*;

@Component
public class ResearchQualityEngine {
    @Resource
    private ResearchFactVerifier verifier;
    @Resource
    private ResearchSourceLineage lineage;

    public ResearchQualityAssessment assess(ResearchTaskContract contract, List<ResearchEvidenceDocument> docs,
                                             List<StructuredResearchFact> modelCandidates) {
        ResearchQualityAssessment result = new ResearchQualityAssessment();
        result.setContract(contract);
        result.setExtractionMode(modelCandidates.isEmpty() ? "RULES" : "MODEL_CANDIDATES_VALIDATED");
        for (ResearchEvidenceDocument doc : docs) {
            ResearchEvidenceReview review = new ResearchEvidenceReview();
            review.setEvidenceRef(doc.getReference());
            review.setUrl(doc.getUrl());
            String cleaned = WebContentText.clean(doc.getText());
            if (cleaned == null || cleaned.isBlank()) {
                review.getIssues().add(ResearchEvidenceIssue.NOISY_CONTENT);
            }
            if (!verifier.matchesEntity(contract, text(doc.getText()))) {
                review.getIssues().add(ResearchEvidenceIssue.ENTITY_MISMATCH);
            } else if (!doc.isSelected()) {
                review.getIssues().add(ResearchEvidenceIssue.EVENT_MISMATCH);
            }
            List<StructuredResearchFact> facts = verifier.extract(contract, doc);
            boolean documentEligible = review.getIssues().isEmpty();
            for (StructuredResearchFact candidate : modelCandidates) {
                if (doc.getReference().equals(candidate.getEvidenceRef())) {
                    facts.add(verifier.verify(contract, doc, candidate));
                }
            }
            Set<String> seen = new HashSet<>();
            for (StructuredResearchFact fact : facts) {
                String key = fact.getKind() + "|" + fact.getValue() + "|" + fact.getUnit() + "|" + fact.getQuote();
                if (!seen.add(key)) {
                    continue;
                }
                // A grounded quote in a rejected source is not approved evidence for this task.
                fact.setVerified(fact.isVerified() && documentEligible);
                result.getFacts().add(fact);
                for (ResearchEvidenceIssue issue : fact.getIssues()) {
                    if (!review.getIssues().contains(issue)) {
                        review.getIssues().add(issue);
                    }
                }
            }
            // A bad candidate cannot poison a separate valid fact from the same document.
            boolean hasVerified = facts.stream().anyMatch(StructuredResearchFact::isVerified);
            review.setAccepted(doc.isSelected() && hasVerified && cleaned != null && !cleaned.isBlank());
            result.getEvidence().add(review);
        }
        result.setIndependentSourceCount(lineage.assign(docs, result.getEvidence()));
        Set<ResearchFactKind> covered = EnumSet.noneOf(ResearchFactKind.class);
        for (StructuredResearchFact fact : result.getFacts()) {
            if (fact.isVerified()) {
                covered.add(fact.getKind());
            }
        }
        result.setCoveredFacts(new ArrayList<>(covered));
        for (ResearchFactKind kind : contract.getRequiredFacts()) {
            if (!covered.contains(kind)) {
                result.getMissingConditions().add("任务事实缺失：" + kind.getLabel());
            }
        }
        result.setTaskCoverage((double) contract.getRequiredFacts().stream().filter(covered::contains).count()
                / Math.max(1, contract.getRequiredFacts().size()));
        if (contract.isDateRequired() && result.getFacts().stream().noneMatch(
                fact -> fact.isVerified() && fact.getEventDate() != null)) {
            result.getMissingConditions().add("事件日期未核实：" + contract.getDateFrom() + " 至 " + contract.getDateTo());
        }
        detectConflicts(result);
        if (result.getIndependentSourceCount() < 2) {
            result.getMissingConditions().add("来源谱系合并后不足两个独立来源组");
        }
        addFeedback(result);
        return result;
    }

    private void detectConflicts(ResearchQualityAssessment result) {
        Map<String, List<StructuredResearchFact>> observed = new LinkedHashMap<>();
        for (StructuredResearchFact fact : result.getFacts()) {
            if (!fact.isVerified() || !verifier.numeric(fact.getKind()) || fact.getScope() == null
                    || fact.getEventDate() == null) {
                continue;
            }
            String key = fact.getSubject() + "|" + fact.getKind() + "|" + fact.getEventDate() + "|" + fact.getScope();
            List<StructuredResearchFact> previousFacts = observed.computeIfAbsent(key, ignored -> new ArrayList<>());
            for (StructuredResearchFact previous : previousFacts) {
                if (verifier.conflictingValues(previous, fact)) {
                    String conflict = fact.getSubject() + "在" + fact.getEventDate() + "的" + fact.getKind().getLabel()
                            + "（同一统计口径）：" + previous.getValueRelation().getLabel() + previous.getValue() + previous.getUnit()
                            + "（" + previous.getEvidenceRef() + "）与" + fact.getValueRelation().getLabel() + fact.getValue()
                            + fact.getUnit() + "（" + fact.getEvidenceRef() + "）不一致，须回查原始披露";
                    if (!result.getConflicts().contains(conflict)) {
                        result.getConflicts().add(conflict);
                    }
                }
            }
            previousFacts.add(fact);
        }
    }

    private void addFeedback(ResearchQualityAssessment result) {
        Set<ResearchEvidenceIssue> issues = EnumSet.noneOf(ResearchEvidenceIssue.class);
        for (ResearchEvidenceReview review : result.getEvidence()) {
            issues.addAll(review.getIssues());
        }
        String focus = result.getContract().getSubject() + " " + text(result.getContract().getSubjectCode())
                + " " + result.getContract().getEvent();
        String repair = " 原始公告 全文 核实事实";
        result.setRecoveryIntent("PRIMARY");
        if (issues.contains(ResearchEvidenceIssue.ENTITY_MISMATCH)) {
            result.getFeedback().add("主体不符：下一轮带公司全称和证券代码，排除同名主体");
        }
        if (issues.contains(ResearchEvidenceIssue.DATE_MISSING) || issues.contains(ResearchEvidenceIssue.DATE_MISMATCH)) {
            result.getFeedback().add("日期未通过：按契约时间窗检索，核实事件日期，不能用网页发布时间代替");
            repair = " " + text(result.getContract().getDateFrom()) + " " + text(result.getContract().getDateTo()) + " 事件日期 公告";
            result.setRecoveryIntent("UPDATE");
        }
        if (issues.contains(ResearchEvidenceIssue.SNIPPET_ONLY) || issues.contains(ResearchEvidenceIssue.NOISY_CONTENT)) {
            result.getFeedback().add("正文不足：检索原始披露全文或更换可读取来源；摘要不计已核验事实");
        }
        if (result.getIndependentSourceCount() < 2) {
            result.getFeedback().add("来源不足：合并同域、显式转载和高度重合正文，下一轮寻找独立原始披露");
        }
        if (!result.getConflicts().isEmpty()) {
            result.getFeedback().add("数值冲突：按同一日期和口径回查公告，解决前禁止自动完成");
            String metrics = result.getFacts().stream().filter(fact -> fact.isVerified() && verifier.numeric(fact.getKind()))
                    .map(fact -> fact.getKind().getLabel()).distinct().collect(java.util.stream.Collectors.joining(" "));
            repair = " " + text(result.getContract().getDateFrom()) + " " + metrics + " 原始披露 更正";
        }
        result.getFeedback().addAll(result.getMissingConditions());
        String query = (focus + repair).replaceAll("\\s+", " ").trim();
        result.setRecoveryQuery(query.substring(0, Math.min(180, query.length())));
    }

    private String text(String value) {
        return value == null ? "" : value;
    }
}
