package com.finscope.service.research.report;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ResearchReportBlueprint {
    private String directAnswer;
    private String direction;
    private String confidence;
    private String confidenceBasis;
    private String timeRange;
    private List<String> definitions = new ArrayList<String>();
    private List<String> excludedQuestions = new ArrayList<String>();
    private List<KeyInsight> keyInsights = new ArrayList<KeyInsight>();
    private List<SubQuestion> subQuestions = new ArrayList<SubQuestion>();
    private List<ArgumentChain> argumentChains = new ArrayList<ArgumentChain>();
    private Counterargument strongestCounterargument;
    private List<Scenario> scenarios = new ArrayList<Scenario>();
    private List<String> knowledgeTakeaways = new ArrayList<String>();
    private List<String> unknowns = new ArrayList<String>();
    private List<WatchItem> watchItems = new ArrayList<WatchItem>();

    @Data public static class KeyInsight {
        private String finding;
        private String meaning;
        private List<String> evidenceRefs = new ArrayList<String>();
    }

    @Data public static class SubQuestion {
        private String key;
        private String question;
        private String answer;
        private List<String> evidenceRefs = new ArrayList<String>();
        private List<String> counterEvidenceRefs = new ArrayList<String>();
        private String impact;
        private List<String> unknowns = new ArrayList<String>();
    }

    @Data public static class ArgumentChain {
        private String fact;
        private String inference;
        private String judgment;
        private String alternativeExplanation;
        private List<String> evidenceRefs = new ArrayList<String>();
    }

    @Data public static class Counterargument {
        private String claim;
        private List<String> evidenceRefs = new ArrayList<String>();
        private String response;
        private List<String> becomesDominantWhen = new ArrayList<String>();
    }

    @Data public static class Scenario {
        private String name;
        private String trigger;
        private String mechanism;
        private String observableResult;
        private String impact;
        private List<String> evidenceRefs = new ArrayList<String>();
    }

    @Data public static class WatchItem {
        private String metric;
        private String baseline;
        private String frequency;
        private String upgradeCondition;
        private String downgradeCondition;
    }
}
