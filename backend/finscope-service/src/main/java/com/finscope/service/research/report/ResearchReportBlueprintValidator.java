package com.finscope.service.research.report;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class ResearchReportBlueprintValidator {
    public List<String> validate(ResearchReportBlueprint value, List<ResearchEvidenceDossier> dossier) {
        List<String> issues = new ArrayList<String>();
        if (value == null || blank(value.getDirectAnswer())) issues.add("ANSWER_NOT_DIRECT");
        if (value == null) return issues;
        if (value.getKeyInsights() == null || value.getKeyInsights().size() < 3 || value.getKeyInsights().size() > 6) {
            issues.add("INVALID_KEY_INSIGHT_COUNT");
        }
        if (value.getSubQuestions() == null || value.getSubQuestions().size() < 3 || value.getSubQuestions().size() > 6) {
            issues.add("INVALID_SUBQUESTION_COUNT");
        }
        if (value.getArgumentChains() == null || value.getArgumentChains().size() < 2) {
            issues.add("INSUFFICIENT_ARGUMENT_CHAINS");
        }
        validateSubQuestionKeys(value, issues);
        Set<String> allowed = allowedReferences(dossier);
        validateReferences(value, allowed, issues);
        validateGrounding(value, dossier, issues);
        ResearchReportBlueprint.Counterargument counter = value.getStrongestCounterargument();
        if (counter == null || blank(counter.getClaim()) || blank(counter.getResponse())
                || empty(counter.getEvidenceRefs())
                || counter.getBecomesDominantWhen() == null || counter.getBecomesDominantWhen().isEmpty()) {
            issues.add("WEAK_COUNTERARGUMENT");
        }
        return distinct(issues);
    }

    private void validateSubQuestionKeys(ResearchReportBlueprint value, List<String> issues) {
        Set<String> keys = new HashSet<String>();
        if (value.getSubQuestions() == null) return;
        for (ResearchReportBlueprint.SubQuestion item : value.getSubQuestions()) {
            if (item == null || blank(item.getKey()) || !item.getKey().matches("[a-z][a-z0-9_]{2,47}")
                    || !keys.add(item.getKey())) issues.add("INVALID_SUBQUESTION_KEY");
        }
    }

    private void validateReferences(ResearchReportBlueprint value, Set<String> allowed, List<String> issues) {
        if (value.getKeyInsights() != null) for (ResearchReportBlueprint.KeyInsight item : value.getKeyInsights())
            checkRefs(item == null ? null : item.getEvidenceRefs(), allowed, issues);
        if (value.getSubQuestions() != null) for (ResearchReportBlueprint.SubQuestion item : value.getSubQuestions()) {
            checkRefs(item == null ? null : item.getEvidenceRefs(), allowed, issues);
            checkRefs(item == null ? null : item.getCounterEvidenceRefs(), allowed, issues);
        }
        if (value.getArgumentChains() != null) for (ResearchReportBlueprint.ArgumentChain item : value.getArgumentChains())
            checkRefs(item == null ? null : item.getEvidenceRefs(), allowed, issues);
        if (value.getStrongestCounterargument() != null)
            checkRefs(value.getStrongestCounterargument().getEvidenceRefs(), allowed, issues);
        if (value.getScenarios() != null) for (ResearchReportBlueprint.Scenario item : value.getScenarios())
            checkRefs(item == null ? null : item.getEvidenceRefs(), allowed, issues);
    }

    private void validateGrounding(ResearchReportBlueprint value,
                                   List<ResearchEvidenceDossier> dossier,
                                   List<String> issues) {
        Set<String> used = new HashSet<String>();
        if (value.getKeyInsights() != null) for (ResearchReportBlueprint.KeyInsight item : value.getKeyInsights()) {
            if (item == null || empty(item.getEvidenceRefs())) issues.add("UNGROUNDED_KEY_INSIGHT");
            else used.addAll(item.getEvidenceRefs());
        }
        if (value.getSubQuestions() != null) for (ResearchReportBlueprint.SubQuestion item : value.getSubQuestions()) {
            if (item == null || empty(item.getEvidenceRefs())) issues.add("UNGROUNDED_SUBQUESTION");
            else used.addAll(item.getEvidenceRefs());
            if (item != null && item.getCounterEvidenceRefs() != null) used.addAll(item.getCounterEvidenceRefs());
        }
        if (value.getArgumentChains() != null) for (ResearchReportBlueprint.ArgumentChain item : value.getArgumentChains()) {
            if (item == null || empty(item.getEvidenceRefs())) issues.add("UNGROUNDED_ARGUMENT_CHAIN");
            else used.addAll(item.getEvidenceRefs());
        }
        ResearchReportBlueprint.Counterargument counter = value.getStrongestCounterargument();
        if (counter != null && counter.getEvidenceRefs() != null) used.addAll(counter.getEvidenceRefs());
        if (value.getScenarios() != null) for (ResearchReportBlueprint.Scenario item : value.getScenarios()) {
            if (item != null && item.getEvidenceRefs() != null) used.addAll(item.getEvidenceRefs());
        }
        if (dossier != null && dossier.size() >= 3 && used.size() * 2 < dossier.size()) {
            issues.add("INSUFFICIENT_BLUEPRINT_CITATION_COVERAGE");
        }
        Set<String> counterEvidence = new HashSet<String>();
        if (dossier != null) for (ResearchEvidenceDossier item : dossier) {
            if (item != null && "COUNTER".equals(item.getStance())) counterEvidence.add(item.getEvidenceRef());
        }
        if (!counterEvidence.isEmpty()) {
            Set<String> counterUsed = counter == null || counter.getEvidenceRefs() == null
                    ? new HashSet<String>() : new HashSet<String>(counter.getEvidenceRefs());
            counterUsed.retainAll(counterEvidence);
            if (counterUsed.isEmpty()) issues.add("COUNTER_EVIDENCE_NOT_USED");
        }
    }

    private void checkRefs(List<String> refs, Set<String> allowed, List<String> issues) {
        if (refs == null) return;
        for (String ref : refs) if (!allowed.contains(ref)) issues.add("INVALID_EVIDENCE_REF:" + ref);
    }

    private Set<String> allowedReferences(List<ResearchEvidenceDossier> dossier) {
        Set<String> result = new HashSet<String>();
        if (dossier != null) for (ResearchEvidenceDossier item : dossier) if (item != null) result.add(item.getEvidenceRef());
        return result;
    }

    private List<String> distinct(List<String> values) { return new ArrayList<String>(new java.util.LinkedHashSet<String>(values)); }
    private boolean empty(List<String> values) { return values == null || values.isEmpty(); }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
