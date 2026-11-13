package com.finscope.domain.fundamentals;

import com.finscope.common.enums.fundamentals.FundamentalFindingSupport;
import com.finscope.domain.learningcard.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FundamentalBusinessProjectorTest {
    @Test
    void businessFindingRetainsItsSupportAndOnlyItsOwnDimensionEvidence() {
        var run = run();
        var findings = new FundamentalBusinessProjector().project(run);
        assertEquals(1, findings.size());
        assertEquals("revenueModel", findings.get(0).getNoteField());
        assertEquals(FundamentalFindingSupport.PARTIALLY_SUPPORTED, findings.get(0).getSupport());
        assertEquals("https://example.com/company", findings.get(0).getSources().get(0).getUrl());
        assertEquals(10L, findings.get(0).getRunId());
        run.getEvidence().get(0).setDimensionCode("COMPETITION");
        assertTrue(new FundamentalBusinessProjector().project(run).isEmpty());
    }

    @Test
    void neverPromotesUnverifiedUnsupportedOrUnsafeContentIntoNotes() {
        var run = run();
        var section = run.getClaims().get(0).getSections().get(0);
        section.setVerificationStatus("UNVERIFIED");
        assertTrue(new FundamentalBusinessProjector().project(run).isEmpty());
        section.setVerificationStatus("SUPPORTED");
        section.setEvidenceRefs(List.of("missing"));
        assertTrue(new FundamentalBusinessProjector().project(run).isEmpty());
        section.setEvidenceRefs(List.of("E1"));
        run.getEvidence().get(0).setUrl("javascript:alert(1)");
        assertTrue(new FundamentalBusinessProjector().project(run).isEmpty());
        run.getEvidence().get(0).setUrl("/relative");
        assertTrue(new FundamentalBusinessProjector().project(run).isEmpty());
        section.setVerificationStatus(null);
        assertTrue(new FundamentalBusinessProjector().project(run).isEmpty());
    }

    @Test
    void keepsCounterEvidenceVisibleAndDoesNotReadAnInProgressRun() {
        var run = run();
        run.getClaims().get(0).getSections().get(0).setVerificationStatus("CONTRADICTED");
        assertEquals(FundamentalFindingSupport.CONTRADICTED, new FundamentalBusinessProjector().project(run).get(0).getSupport());
        run.setStatus("RUNNING");
        assertTrue(new FundamentalBusinessProjector().project(run).isEmpty());
    }

    private StockLearningCardRun run() {
        var run = new StockLearningCardRun();
        run.setId(10L);
        run.setStatus("READY");
        var claim = new StockLearningCardClaim();
        claim.setStatus("READY");
        claim.setDimensionCode("PROFIT_MODEL");
        var section = new StockLearningCardSection();
        section.setKey("revenue_engine");
        section.setContent("公开资料中的主营业务认识");
        section.setVerificationStatus("PARTIALLY_SUPPORTED");
        section.setEvidenceRefs(List.of("E1"));
        claim.setSections(List.of(section));
        run.setClaims(List.of(claim));
        var evidence = new StockLearningCardEvidence("E1", "公司资料", "https://example.com/company", "example.com", null, "公开公司介绍正文");
        evidence.setDimensionCode("PROFIT_MODEL");
        run.setEvidence(List.of(evidence));
        return run;
    }
}
