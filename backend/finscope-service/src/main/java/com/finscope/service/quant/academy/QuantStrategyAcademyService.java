package com.finscope.service.quant.academy;

import com.finscope.common.enums.quant.QuantStrategyAcademyBuildItemStatus;
import com.finscope.common.enums.quant.QuantStrategyAcademyShelf;
import com.finscope.common.enums.quant.QuantStrategyDraftStatus;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class QuantStrategyAcademyService {
    private static final int BUILD_LIMIT = 6;

    @Resource private QuantStrategyCatalogRepository catalogRepository;
    @Resource private QuantExperimentRepository experimentRepository;
    @Resource private QuantDatasetService datasets;
    @Resource private QuantStrategyCandidateDraftService drafts;
    @Resource private QuantStrategyService strategies;
    @Resource private QuantExperimentService experiments;
    @Resource private QuantStrategyEvidenceScorer scorer;

    public List<QuantStrategyAcademyCard> cards() {
        return cards(null);
    }

    public List<QuantStrategyAcademyCard> cards(Long datasetId) {
        QuantDataset selectedDataset = datasetId == null ? null : academyDataset(datasetId);
        List<QuantStrategyAcademyCard> cards = new ArrayList<QuantStrategyAcademyCard>();
        List<QuantStrategyCandidate> candidates = catalogRepository.findCandidates("ADAPTABLE", null);
        for (QuantStrategyCandidate candidate : candidates) {
            boolean legacyOrigin = false;
            Optional<Long> versionId = selectedDataset == null
                    ? catalogRepository.findLatestVersionIdByCandidateAndSourceCommit(candidate.getId(), candidate.getSourceCommitSha())
                    : catalogRepository.findLatestVersionIdByCandidateAndDatasetAndSourceCommit(candidate.getId(),
                            selectedDataset.getId(), candidate.getSourceCommitSha());
            if (versionId.isEmpty() && selectedDataset != null) {
                versionId = catalogRepository.findLatestLegacyVersionIdByCandidateAndDataset(candidate.getId(),
                        selectedDataset.getId());
                legacyOrigin = versionId.isPresent();
            }
            if (versionId.isEmpty()) {
                cards.add(cardWithoutVersion(candidate, selectedDataset));
                continue;
            }
            QuantStrategyVersion version = strategies.getVersion(versionId.get());
            QuantDataset dataset = datasets.get(version.getDatasetId());
            QuantExperiment experiment = experimentRepository.findLatestByStrategyVersion(version.getId()).orElse(null);
            QuantStrategyAcademyCard card = scorer.score(candidate, dataset, experiment);
            card.setStrategyVersionId(version.getId());
            if (legacyOrigin) {
                markLegacyOrigin(card);
            }
            cards.add(card);
        }
        return cards;
    }

    public synchronized QuantStrategyAcademyBuildResult build(Long datasetId) {
        if (datasetId == null) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "数据集不能为空");
        }
        QuantDataset dataset = academyDataset(datasetId);
        List<QuantStrategyCandidate> all = catalogRepository.findCandidates("ADAPTABLE", null);
        List<QuantStrategyCandidate> selected = all.subList(0, Math.min(BUILD_LIMIT, all.size()));
        QuantStrategyAcademyBuildResult result = new QuantStrategyAcademyBuildResult();
        result.setScannedCount(selected.size());
        for (QuantStrategyCandidate candidate : selected) {
            buildCandidate(candidate, datasetId, result);
        }
        return result;
    }

    private QuantDataset academyDataset(Long datasetId) {
        QuantDataset dataset = datasets.get(datasetId);
        if (!"READY".equals(dataset.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "策略学院只能使用已通过质量门禁的数据集");
        }
        if (!"REAL".equals(dataset.getDataKind())) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "策略学院只能使用真实研究数据集");
        }
        return dataset;
    }

    private QuantStrategyAcademyCard cardWithoutVersion(QuantStrategyCandidate candidate, QuantDataset selectedDataset) {
        Optional<Long> draftId = selectedDataset == null
                ? catalogRepository.findLatestDraftIdByCandidateAndSourceCommit(candidate.getId(), candidate.getSourceCommitSha())
                : catalogRepository.findLatestDraftIdByCandidateAndDatasetAndSourceCommit(candidate.getId(),
                        selectedDataset.getId(), candidate.getSourceCommitSha());
        boolean legacyOrigin = false;
        if (draftId.isEmpty() && selectedDataset != null) {
            draftId = catalogRepository.findLatestLegacyDraftIdByCandidateAndDataset(candidate.getId(),
                    selectedDataset.getId());
            legacyOrigin = draftId.isPresent();
        }
        if (draftId.isEmpty()) {
            return scorer.score(candidate, selectedDataset, null);
        }
        QuantStrategyDraft draft = strategies.getDraft(draftId.get());
        QuantDataset dataset = selectedDataset == null ? datasets.get(draft.getDatasetId()) : selectedDataset;
        if (QuantStrategyDraftStatus.FAILED.equals(draft.getStatus())
                || QuantStrategyDraftStatus.BUILD_FAILED.equals(draft.getStatus())) {
            QuantStrategyAcademyCard card = scorer.failedDraft(candidate, dataset, draft);
            if (legacyOrigin) {
                markLegacyOrigin(card);
            }
            return card;
        }
        QuantStrategyAcademyCard card = scorer.score(candidate, dataset, null);
        if (legacyOrigin) {
            markLegacyOrigin(card);
        }
        return card;
    }

    private void markLegacyOrigin(QuantStrategyAcademyCard card) {
        if (QuantStrategyAcademyShelf.APPLICATION_CANDIDATE.equals(card.getShelf())) {
            card.setShelf(QuantStrategyAcademyShelf.OBSERVATION);
            card.setEvidenceSummary("历史实验可供参考，但来源提交未知，不能进入应用候选");
        }
        card.getLimitations().add("此记录来自升级前的历史关联，无法确认对应的公开来源提交；仅展示，不会复用");
    }

    private void buildCandidate(QuantStrategyCandidate candidate, Long datasetId,
                                    QuantStrategyAcademyBuildResult result) {
        QuantStrategyAcademyBuildResult.BuildItem item = new QuantStrategyAcademyBuildResult.BuildItem();
        item.setCandidateId(candidate.getId());
        item.setTitle(candidate.getTitle());
        result.getItems().add(item);
        Long draftId = null;
        try {
            Optional<Long> existingVersionId = catalogRepository.findLatestVersionIdByCandidateAndDatasetAndSourceCommit(
                    candidate.getId(), datasetId, candidate.getSourceCommitSha());
            if (existingVersionId.isPresent()) {
                reuseOrRun(existingVersionId.get(), item, result);
                return;
            }
            QuantStrategyDraft draft = drafts.generate(candidate.getId(), datasetId);
            draftId = draft.getId();
            if (!QuantStrategyDraftStatus.VALIDATED.equals(draft.getStatus())) {
                item.setStatus(QuantStrategyAcademyBuildItemStatus.FAILED);
                item.setMessage(draft.getValidationIssues().isEmpty() ? "策略草案未通过协议"
                        : String.join("；", draft.getValidationIssues()));
                result.setFailedCount(result.getFailedCount() + 1);
                return;
            }
            result.setDraftCreatedCount(result.getDraftCreatedCount() + 1);
            QuantStrategyVersion version = strategies.confirm(draft.getId());
            item.setStrategyVersionId(version.getId());
            result.setVersionConfirmedCount(result.getVersionConfirmedCount() + 1);
            startExperiment(version.getId(), item, result);
        } catch (RuntimeException ex) {
            String message = safeMessage(ex);
            if (draftId != null && item.getStrategyVersionId() == null) {
                strategies.recordDraftBuildFailure(draftId, message);
            }
            item.setStatus(QuantStrategyAcademyBuildItemStatus.FAILED);
            item.setMessage(message);
            result.setFailedCount(result.getFailedCount() + 1);
            log.warn("策略学院候选构建失败 candidateId={} errorType={}", candidate.getId(),
                    ex.getClass().getSimpleName());
        }
    }

    private void reuseOrRun(Long versionId, QuantStrategyAcademyBuildResult.BuildItem item,
                                QuantStrategyAcademyBuildResult result) {
        item.setStrategyVersionId(versionId);
        Optional<QuantExperiment> latest = experimentRepository.findLatestByStrategyVersion(versionId);
        if (latest.isPresent() && ("QUEUED".equals(latest.get().getStatus())
                || "RUNNING".equals(latest.get().getStatus()) || "SUCCEEDED".equals(latest.get().getStatus()))) {
            item.setExperimentId(latest.get().getId());
            item.setStatus(QuantStrategyAcademyBuildItemStatus.REUSED);
            item.setMessage("复用已有策略版本与实验记录");
            result.setReusedCount(result.getReusedCount() + 1);
            return;
        }
        startExperiment(versionId, item, result);
    }

    private void startExperiment(Long versionId, QuantStrategyAcademyBuildResult.BuildItem item,
                                     QuantStrategyAcademyBuildResult result) {
        QuantExperiment experiment = experiments.create(versionId);
        item.setExperimentId(experiment.getId());
        item.setStatus(QuantStrategyAcademyBuildItemStatus.STARTED);
        item.setMessage("已进入本地历史验证队列");
        result.setExperimentStartedCount(result.getExperimentStartedCount() + 1);
    }

    private String safeMessage(RuntimeException error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "候选策略构建失败";
        }
        return message.length() > 200 ? message.substring(0, 200) : message;
    }
}
