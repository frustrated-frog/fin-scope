package com.finscope.domain.financials;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class BrokerResearchAnalysis {
    private List<String> executiveSummary = new ArrayList<String>();
    private List<String> investmentThesis = new ArrayList<String>();
    private List<String> businessAnalysis = new ArrayList<String>();
    private List<String> industryAnalysis = new ArrayList<String>();
    private List<String> keyAssumptions = new ArrayList<String>();
    private List<String> catalysts = new ArrayList<String>();
    private List<String> risks = new ArrayList<String>();
    private List<String> learningNotes = new ArrayList<String>();
    private Map<String, List<EvidencePoint>> evidenceSections =
            new LinkedHashMap<String, List<EvidencePoint>>();
    private List<GlossaryItem> glossary = new ArrayList<GlossaryItem>();
    private List<String> limitations = new ArrayList<String>();
    private String disclaimer = "仅供研究学习，不构成投资建议。";

    @Data
    public static class GlossaryItem {
        private String term;
        private String explanation;
    }

    @Data
    public static class EvidencePoint {
        private String text;
        private String sourceQuote;
        private Integer sourcePage;
    }
}
