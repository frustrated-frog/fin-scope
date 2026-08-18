package com.finscope.service.quant.academy;

import com.finscope.common.enums.quant.QuantStrategyEvidenceLevel;
import com.finscope.common.exception.BusinessException;
import com.finscope.dao.quant.QuantExperimentRepository;
import com.finscope.dao.quant.QuantStrategyCatalogRepository;
import com.finscope.domain.quant.academy.QuantStrategyAcademyBuildResult;
import com.finscope.domain.quant.academy.QuantStrategyAcademyCard;
import com.finscope.domain.quant.catalog.QuantStrategyCandidate;
import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.domain.quant.experiment.QuantExperiment;
import com.finscope.domain.quant.strategy.QuantStrategyDraft;
import com.finscope.domain.quant.strategy.QuantStrategyVersion;
import com.finscope.service.quant.catalog.QuantStrategyCandidateDraftService;
import com.finscope.service.quant.data.QuantDatasetService;
import com.finscope.service.quant.experiment.QuantExperimentService;
import com.finscope.service.quant.strategy.QuantStrategyService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuantStrategyAcademyServiceTest {

    @Test
    void buildsAtMostSixCandidatesAndIsolatesOneDraftFailure() {
        Fixture fixture = fixture();
        List<QuantStrategyCandidate> candidates = candidates(7);
        when(fixture.catalog.findCandidates("ADAPTABLE", null)).thenReturn(candidates);
        when(fixture.catalog.findLatestVersionIdByCandidateAndDatasetAndSourceCommit(anyLong(),
                org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.eq("source-sha")))
                .thenReturn(Optional.<Long>empty());
        for (int index = 0; index < 6; index++) {
            QuantStrategyCandidate candidate = candidates.get(index);
            if (index == 2) {
                when(fixture.drafts.generate(candidate.getId(), 3L)).thenThrow(new BusinessException(
                        com.finscope.common.exception.ErrorCode.BUSINESS_CONFLICT, "草案协议未通过"));
            } else {
                QuantStrategyDraft draft = new QuantStrategyDraft();
                draft.setId(100L + index);
                draft.setStatus("VALIDATED");
                when(fixture.drafts.generate(candidate.getId(), 3L)).thenReturn(draft);
                QuantStrategyVersion version = new QuantStrategyVersion();
                version.setId(200L + index);
                when(fixture.strategies.confirm(draft.getId())).thenReturn(version);
                QuantExperiment experiment = new QuantExperiment();
                experiment.setId(300L + index);
                experiment.setStatus("QUEUED");
                when(fixture.experiments.create(version.getId())).thenReturn(experiment);
            }
        }

        QuantStrategyAcademyBuildResult result = fixture.service.build(3L);

        assertEquals(6, result.getScannedCount());
        assertEquals(5, result.getDraftCreatedCount());
        assertEquals(5, result.getVersionConfirmedCount());
        assertEquals(5, result.getExperimentStartedCount());
        assertEquals(1, result.getFailedCount());
        assertEquals(6, result.getItems().size());
        verify(fixture.drafts, times(6)).generate(anyLong(), org.mockito.ArgumentMatchers.eq(3L));
        verify(fixture.drafts, never()).generate(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(3L));
    }

    @Test
    void reusesExistingVersionAndActiveExperiment() {
        Fixture fixture = fixture();
        QuantStrategyCandidate candidate = candidates(1).get(0);
        when(fixture.catalog.findCandidates("ADAPTABLE", null)).thenReturn(Collections.singletonList(candidate));
        when(fixture.catalog.findLatestVersionIdByCandidateAndDatasetAndSourceCommit(candidate.getId(), 3L, "source-sha"))
                .thenReturn(Optional.of(21L));
        QuantExperiment running = new QuantExperiment();
        running.setId(31L);
        running.setStatus("RUNNING");
        when(fixture.experimentRepository.findLatestByStrategyVersion(21L)).thenReturn(Optional.of(running));

        QuantStrategyAcademyBuildResult result = fixture.service.build(3L);

        assertEquals(1, result.getReusedCount());
        assertEquals(0, result.getExperimentStartedCount());
        verify(fixture.drafts, never()).generate(anyLong(), anyLong());
        verify(fixture.experiments, never()).create(anyLong());
    }

    @Test
    void rejectsLearningDatasetBeforeGeneratingAnything() {
        Fixture fixture = fixture();
        fixture.dataset.setDataKind("LEARNING_SAMPLE");

        assertThrows(BusinessException.class, () -> fixture.service.build(3L));

        verify(fixture.catalog, never()).findCandidates("ADAPTABLE", null);
    }

    @Test
    void generatesANewVersionWhenOnlyAnotherDatasetsVersionExists() {
        Fixture fixture = fixture();
        QuantStrategyCandidate candidate = candidates(1).get(0);
        when(fixture.catalog.findCandidates("ADAPTABLE", null)).thenReturn(Collections.singletonList(candidate));
        when(fixture.catalog.findLatestVersionIdByCandidateAndDatasetAndSourceCommit(candidate.getId(), 3L, "source-sha"))
                .thenReturn(Optional.<Long>empty());
        QuantStrategyDraft draft = new QuantStrategyDraft();
        draft.setId(101L);
        draft.setStatus("VALIDATED");
        when(fixture.drafts.generate(candidate.getId(), 3L)).thenReturn(draft);
        QuantStrategyVersion version = new QuantStrategyVersion();
        version.setId(201L);
        when(fixture.strategies.confirm(101L)).thenReturn(version);
        QuantExperiment experiment = new QuantExperiment();
        experiment.setId(301L);
        when(fixture.experiments.create(201L)).thenReturn(experiment);

        QuantStrategyAcademyBuildResult result = fixture.service.build(3L);

        assertEquals(1, result.getVersionConfirmedCount());
        assertEquals(0, result.getReusedCount());
        verify(fixture.drafts).generate(candidate.getId(), 3L);
    }

    @Test
    void recordsAValidatedDraftWhenVersionConfirmationFails() {
        Fixture fixture = fixture();
        QuantStrategyCandidate candidate = candidates(1).get(0);
        when(fixture.catalog.findCandidates("ADAPTABLE", null)).thenReturn(Collections.singletonList(candidate));
        when(fixture.catalog.findLatestVersionIdByCandidateAndDatasetAndSourceCommit(candidate.getId(), 3L, "source-sha"))
                .thenReturn(Optional.<Long>empty());
        QuantStrategyDraft draft = new QuantStrategyDraft();
        draft.setId(101L);
        draft.setStatus("VALIDATED");
        when(fixture.drafts.generate(candidate.getId(), 3L)).thenReturn(draft);
        when(fixture.strategies.confirm(101L)).thenThrow(new BusinessException(
                com.finscope.common.exception.ErrorCode.BUSINESS_CONFLICT, "策略版本确认失败"));

        QuantStrategyAcademyBuildResult result = fixture.service.build(3L);

        assertEquals(1, result.getFailedCount());
        verify(fixture.strategies).recordDraftFailure(101L, "策略版本确认失败");
    }

    @Test
    void aggregatesLatestRealExperimentIntoAcademyCard() {
        Fixture fixture = fixture();
        QuantStrategyCandidate candidate = candidates(1).get(0);
        when(fixture.catalog.findCandidates("ADAPTABLE", null)).thenReturn(Collections.singletonList(candidate));
        when(fixture.catalog.findLatestVersionIdByCandidateAndDatasetAndSourceCommit(candidate.getId(), 3L, "source-sha"))
                .thenReturn(Optional.of(21L));
        QuantStrategyVersion version = new QuantStrategyVersion();
        version.setId(21L);
        version.setDatasetId(3L);
        when(fixture.strategies.getVersion(21L)).thenReturn(version);
        QuantExperiment experiment = new QuantExperiment();
        experiment.setId(31L);
        experiment.setStatus("RUNNING");
        when(fixture.experimentRepository.findLatestByStrategyVersion(21L)).thenReturn(Optional.of(experiment));
        QuantStrategyAcademyCard scored = new QuantStrategyAcademyCard();
        scored.setCandidateId(candidate.getId());
        scored.setEvidenceLevel(QuantStrategyEvidenceLevel.RESEARCH_REPLICATION);
        when(fixture.scorer.score(candidate, fixture.dataset, experiment)).thenReturn(scored);

        List<QuantStrategyAcademyCard> cards = fixture.service.cards(3L);

        assertEquals(1, cards.size());
        assertEquals(Long.valueOf(21L), cards.get(0).getStrategyVersionId());
        verify(fixture.scorer).score(candidate, fixture.dataset, experiment);
    }

    @Test
    void keepsAFailedDraftVisibleForTheSelectedDataset() {
        Fixture fixture = fixture();
        QuantStrategyCandidate candidate = candidates(1).get(0);
        when(fixture.catalog.findCandidates("ADAPTABLE", null)).thenReturn(Collections.singletonList(candidate));
        when(fixture.catalog.findLatestVersionIdByCandidateAndDatasetAndSourceCommit(candidate.getId(), 3L, "source-sha"))
                .thenReturn(Optional.<Long>empty());
        when(fixture.catalog.findLatestDraftIdByCandidateAndDatasetAndSourceCommit(candidate.getId(), 3L, "source-sha"))
                .thenReturn(Optional.of(11L));
        QuantStrategyDraft failed = new QuantStrategyDraft();
        failed.setId(11L);
        failed.setStatus("FAILED");
        failed.setValidationIssues(Collections.singletonList("换手周期不受支持"));
        when(fixture.strategies.getDraft(11L)).thenReturn(failed);
        QuantStrategyAcademyCard scored = new QuantStrategyAcademyCard();
        scored.setCandidateId(candidate.getId());
        scored.setEvidenceLevel(QuantStrategyEvidenceLevel.LEARNING_CASE);
        when(fixture.scorer.failedDraft(candidate, fixture.dataset, failed)).thenReturn(scored);

        List<QuantStrategyAcademyCard> cards = fixture.service.cards(3L);

        assertEquals(QuantStrategyEvidenceLevel.LEARNING_CASE, cards.get(0).getEvidenceLevel());
        verify(fixture.scorer).failedDraft(candidate, fixture.dataset, failed);
    }

    private Fixture fixture() {
        Fixture fixture = new Fixture();
        fixture.catalog = mock(QuantStrategyCatalogRepository.class);
        fixture.experimentRepository = mock(QuantExperimentRepository.class);
        fixture.datasets = mock(QuantDatasetService.class);
        fixture.drafts = mock(QuantStrategyCandidateDraftService.class);
        fixture.strategies = mock(QuantStrategyService.class);
        fixture.experiments = mock(QuantExperimentService.class);
        fixture.scorer = mock(QuantStrategyEvidenceScorer.class);
        fixture.dataset = new QuantDataset();
        fixture.dataset.setId(3L);
        fixture.dataset.setName("A股真实研究集");
        fixture.dataset.setStatus("READY");
        fixture.dataset.setDataKind("REAL");
        when(fixture.datasets.get(3L)).thenReturn(fixture.dataset);
        fixture.service = new QuantStrategyAcademyService();
        ReflectionTestUtils.setField(fixture.service, "catalogRepository", fixture.catalog);
        ReflectionTestUtils.setField(fixture.service, "experimentRepository", fixture.experimentRepository);
        ReflectionTestUtils.setField(fixture.service, "datasets", fixture.datasets);
        ReflectionTestUtils.setField(fixture.service, "drafts", fixture.drafts);
        ReflectionTestUtils.setField(fixture.service, "strategies", fixture.strategies);
        ReflectionTestUtils.setField(fixture.service, "experiments", fixture.experiments);
        ReflectionTestUtils.setField(fixture.service, "scorer", fixture.scorer);
        return fixture;
    }

    private List<QuantStrategyCandidate> candidates(int count) {
        List<QuantStrategyCandidate> values = new ArrayList<QuantStrategyCandidate>();
        for (int index = 0; index < count; index++) {
            QuantStrategyCandidate value = new QuantStrategyCandidate();
            value.setId((long) index + 1);
            value.setTitle("公开策略 " + (index + 1));
            value.setCompatibilityStatus("ADAPTABLE");
            value.setSourceCommitSha("source-sha");
            value.setMappedFactors(Collections.singletonList("BP"));
            values.add(value);
        }
        return values;
    }

    private static class Fixture {
        private QuantStrategyCatalogRepository catalog;
        private QuantExperimentRepository experimentRepository;
        private QuantDatasetService datasets;
        private QuantStrategyCandidateDraftService drafts;
        private QuantStrategyService strategies;
        private QuantExperimentService experiments;
        private QuantStrategyEvidenceScorer scorer;
        private QuantDataset dataset;
        private QuantStrategyAcademyService service;
    }
}
