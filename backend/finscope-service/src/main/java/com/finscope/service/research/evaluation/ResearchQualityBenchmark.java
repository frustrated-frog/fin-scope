package com.finscope.service.research.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.ResearchThesis;
import com.finscope.domain.research.quality.*;
import com.finscope.service.research.quality.ResearchQualityEngine;
import com.finscope.service.research.quality.ResearchTaskContractBuilder;
import lombok.Data;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;

/** Frozen adversarial cases with external expected labels; no model calls and no tautological truth scoring. */
@Component
public class ResearchQualityBenchmark {
    public static final String VERSION = "task-quality-benchmark-v1";
    @Resource
    private ResearchQualityEngine engine;
    @Resource
    private ResearchTaskContractBuilder contractBuilder;
    private final ObjectMapper mapper = new ObjectMapper();

    public List<Result> run(InputStream input) {
        try {
            List<Fixture> fixtures = mapper.readValue(input, new TypeReference<List<Fixture>>() { });
            List<Result> results = new ArrayList<>();
            for (Fixture fixture : fixtures) {
                results.add(evaluate(fixture));
            }
            return results;
        } catch (Exception error) {
            throw new IllegalStateException("研究质量基准读取失败", error);
        }
    }

    private Result evaluate(Fixture fixture) {
        ResearchRun run = new ResearchRun();
        run.setId(1L);
        run.setRunDate(LocalDate.parse(fixture.getAsOfDate()));
        ResearchThesis thesis = new ResearchThesis();
        thesis.setQuestion(fixture.getQuestion());
        thesis.setSubjectName(fixture.getSubject());
        ResearchTaskContract contract = contractBuilder.build(run, thesis);
        ResearchQualityAssessment assessment = engine.assess(contract, fixture.getDocuments(), fixture.getCandidates());
        int accepted = 0;
        int falseAccepted = 0;
        int supported = 0;
        for (StructuredResearchFact fact : assessment.getFacts()) {
            if (!fact.isVerified()) {
                continue;
            }
            accepted++;
            boolean expected = fixture.isExpectedAccepted() && (fact.getValue() == null
                    || fixture.getAllowedNumbers().contains(fact.getKind() + ":" + fact.getValue() + ":" + fact.getUnit()));
            if (!expected) {
                falseAccepted++;
            }
            ResearchEvidenceDocument document = fixture.getDocuments().stream()
                    .filter(doc -> doc.getReference().equals(fact.getEvidenceRef())).findFirst().orElseThrow();
            if (fact.getQuoteStart() >= 0 && fact.getQuoteEnd() <= document.getText().length()
                    && document.getText().substring(fact.getQuoteStart(), fact.getQuoteEnd()).equals(fact.getQuote())) {
                supported++;
            }
        }
        Result result = new Result();
        result.setId(fixture.getId());
        result.setTaskCoverage(assessment.getTaskCoverage());
        result.setFalseFactAcceptanceRate(accepted == 0 ? 0D : (double) falseAccepted / accepted);
        result.setQuoteSupportRate(accepted == 0 ? 1D : (double) supported / accepted);
        result.setIndependentSourceCount(assessment.getIndependentSourceCount());
        result.setConflictDetected(!assessment.getConflicts().isEmpty());
        boolean recovery = true;
        if (!fixture.getBeforeDocuments().isEmpty()) {
            ResearchQualityAssessment before = engine.assess(contract, fixture.getBeforeDocuments(), Collections.emptyList());
            recovery = assessment.getTaskCoverage() > before.getTaskCoverage()
                    || assessment.getIndependentSourceCount() > before.getIndependentSourceCount()
                    || assessment.getConflicts().size() < before.getConflicts().size();
        }
        result.setRecovered(recovery);
        result.setPassed(falseAccepted == 0 && result.getQuoteSupportRate() == 1D && recovery
                && Math.abs(assessment.getTaskCoverage() - fixture.getExpectedCoverage()) < 0.0001D
                && result.isConflictDetected() == fixture.isExpectedConflict()
                && result.getIndependentSourceCount() == fixture.getExpectedSourceGroups());
        return result;
    }

    @Data
    private static class Fixture {
        private String id;
        private String subject;
        private String question;
        private String asOfDate;
        private List<ResearchEvidenceDocument> documents = new ArrayList<>();
        private List<ResearchEvidenceDocument> beforeDocuments = new ArrayList<>();
        private List<StructuredResearchFact> candidates = new ArrayList<>();
        private boolean expectedAccepted;
        private List<String> allowedNumbers = new ArrayList<>();
        private double expectedCoverage;
        private boolean expectedConflict;
        private int expectedSourceGroups;
    }

    @Data
    public static class Result {
        private String id;
        private double falseFactAcceptanceRate;
        private double quoteSupportRate;
        private double taskCoverage;
        private int independentSourceCount;
        private boolean conflictDetected;
        private boolean recovered;
        private boolean passed;
    }
}
