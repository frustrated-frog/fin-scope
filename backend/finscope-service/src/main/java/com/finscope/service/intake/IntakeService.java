package com.finscope.service.intake;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.article.ArticleRepository;
import com.finscope.dao.fetch.FetchRunRepository;
import com.finscope.dao.intake.FetchBatchRepository;
import com.finscope.dao.intake.IntakeCandidateRepository;
import com.finscope.dao.source.SourceRepository;
import com.finscope.domain.article.ArticleIngestResult;
import com.finscope.domain.fetch.FetchRun;
import com.finscope.domain.fetch.RawItem;
import com.finscope.domain.intake.CandidateReview;
import com.finscope.domain.intake.FetchBatch;
import com.finscope.domain.intake.IntakeCandidate;
import com.finscope.domain.intake.IntakeEnums;
import com.finscope.domain.intake.PromoteIntakeCandidateResponse;
import com.finscope.domain.source.Source;
import com.finscope.rpc.source.SourceAdapter;
import com.finscope.rpc.source.SourceAdapterRegistry;
import com.finscope.service.article.ArticleIngestCoordinator;
import com.finscope.service.dedupe.FingerprintService;
import com.finscope.service.fetch.RawItemSelector;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import com.finscope.domain.task.TaskPhase;

@Service
public class IntakeService {
    private static final int LOOKBACK_DAYS = 3;
    private static final Set<String> HUMAN_STATUSES = new HashSet<String>(Arrays.asList(
            IntakeEnums.HUMAN_PENDING, IntakeEnums.HUMAN_SAVED_FOR_LATER,
            IntakeEnums.HUMAN_SKIPPED, IntakeEnums.HUMAN_REJECTED));

    @Resource
    private SourceRepository sourceRepository;
    @Resource
    private FetchBatchRepository fetchBatchRepository;
    @Resource
    private IntakeCandidateRepository candidateRepository;
    @Resource
    private FetchRunRepository fetchRunRepository;
    @Resource
    private SourceAdapterRegistry adapterRegistry;
    @Resource
    private RawItemSelector rawItemSelector;
    @Resource
    private CandidateReviewAgent candidateReviewAgent;
    @Resource
    private BatchSummaryAgent batchSummaryAgent;
    @Resource
    private ArticleIngestCoordinator articleIngestCoordinator;
    @Resource
    private FingerprintService fingerprintService;
    @Resource
    private ArticleRepository articleRepository;
    @Resource
    private PromotionWorkflowService promotionWorkflowService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public FetchBatch intakeFetch(Long sourceId) {
        return intakeFetch(sourceId, IntakeEnums.TRIGGER_MANUAL, null, null);
    }

    public FetchBatch intakeFetch(Long sourceId, String triggerType, String scheduleSlot) {
        return intakeFetch(sourceId, triggerType, scheduleSlot, null);
    }

    public FetchBatch intakeFetch(Long sourceId, String triggerType, String scheduleSlot, Consumer<TaskPhase> progress) {
        Source source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Source not found: " + sourceId));
        if (!source.isEnabled()) throw new IllegalStateException("信息源已归档，无法抓取");
        FetchBatch batch = new FetchBatch();
        batch.setSourceId(source.getId());
        batch.setSourceName(source.getName());
        batch.setTriggerType(triggerType);
        batch.setLookbackDays(LOOKBACK_DAYS);
        batch.setMaxItemsRequested(maxItems(source));
        FetchBatch running = fetchBatchRepository.start(batch);
        FetchRun compatibleRun = fetchRunRepository.start(source.getId(), source.getName());
        int duplicateCount = 0;
        try {
            phase(progress, TaskPhase.FETCHING);
            SourceAdapter adapter = adapterRegistry.get(source);
            List<RawItem> rawItems = emptyIfNull(adapter.fetch(source));
            phase(progress, TaskPhase.PARSING);
            List<RawItem> selected = limit(rawItemSelector.select(source, filterByLookback(rawItems)), maxItems(source));
            List<IntakeCandidate> candidates = new ArrayList<IntakeCandidate>();
            int newCandidateCount = 0;
            for (RawItem item : selected) {
                IntakeCandidate candidate = toCandidate(running, source, item);
                if (isDuplicate(candidate)) {
                    candidate.setAgentRecommendation(IntakeEnums.AGENT_DUPLICATE);
                    candidate.setAgentStatus(IntakeEnums.AGENT_FALLBACK);
                    candidate.setHumanStatus(IntakeEnums.HUMAN_REJECTED);
                    candidate.setDecisionSummary("重复内容：系统已发现相同 URL 的候选或文章。");
                    duplicateCount++;
                } else {
                    phase(progress, TaskPhase.LLM);
                    CandidateReviewAgent.ReviewResult reviewResult = candidateReviewAgent.reviewWithResult(candidate);
                    applyReview(candidate, reviewResult);
                    newCandidateCount++;
                }
                candidates.add(candidateRepository.save(candidate));
            }
            if (newCandidateCount == 0 && candidates.isEmpty()) {
                String message = "没有产出候选内容：可能被最近 3 天窗口过滤、信息源为空或正文抽取失败。";
                FetchBatch failed = fetchBatchRepository.finish(running, IntakeEnums.BATCH_FAILED, rawItems.size(),
                        0, 0, duplicateCount, 0, message, null, message);
                fetchRunRepository.finish(compatibleRun, "FAILED", 0, duplicateCount, message);
                return failed;
            }
            if (newCandidateCount == 0) {
                String message = "本次未产生新候选，已识别 " + duplicateCount + " 条重复内容。";
                FetchBatch completed = fetchBatchRepository.finish(running, IntakeEnums.BATCH_PARTIAL_SUCCESS, rawItems.size(),
                        candidates.size(), 0, duplicateCount, 0, null, null, message);
                fetchRunRepository.finish(compatibleRun, "SUCCESS", 0, duplicateCount, null);
                return completed;
            }
            phase(progress, TaskPhase.LLM);
            BatchSummaryAgent.BatchSummary summary = batchSummaryAgent.summarize(running, candidates);
            String summaryJson = scheduleSlot == null ? summary.getSummaryJson() : scheduledSummaryJson(scheduleSlot, summary);
            FetchBatch completed = fetchBatchRepository.finish(running, IntakeEnums.BATCH_COMPLETED, rawItems.size(),
                    candidates.size(), candidates.size(), duplicateCount, lowValueCount(candidates), null,
                    summaryJson, summary.getSummaryText());
            fetchRunRepository.finish(compatibleRun, "SUCCESS", candidates.size(), duplicateCount, null);
            return completed;
        } catch (Exception ex) {
            FetchBatch failed = fetchBatchRepository.finish(running, IntakeEnums.BATCH_FAILED, 0, 0, 0, duplicateCount,
                    0, ex.getMessage(), null, null);
            fetchRunRepository.finish(compatibleRun, "FAILED", 0, duplicateCount, ex.getMessage());
            return failed;
        }
    }

    public List<FetchBatch> latestBatches() {
        return fetchBatchRepository.latest(30);
    }

    public FetchBatch batch(Long id) {
        return fetchBatchRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Fetch batch not found: " + id));
    }

    public List<IntakeCandidate> candidates(String status, Long batchId, Long sourceId) {
        return candidateRepository.findByStatus(blankDefault(status, IntakeEnums.HUMAN_PENDING), batchId, sourceId);
    }

    public IntakeCandidate candidate(Long id) {
        return candidateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Intake candidate not found: " + id));
    }

    public IntakeCandidate updateHumanStatus(Long id, String status, String note) {
        IntakeCandidate candidate = candidate(id);
        String target = status == null ? "" : status.trim().toUpperCase();
        if (!HUMAN_STATUSES.contains(target)) {
            throw new IllegalArgumentException("Unsupported candidate status: " + status);
        }
        if (IntakeEnums.HUMAN_PROMOTED.equals(candidate.getHumanStatus())) {
            throw new IllegalArgumentException("已入库候选不能变更人工状态");
        }
        candidateRepository.updateHumanStatus(id, target, note);
        return candidate(id);
    }

    public PromoteIntakeCandidateResponse promote(Long id) {
        return promote(id, null);
    }

    public PromoteIntakeCandidateResponse promote(Long id, Consumer<TaskPhase> progress) {
        IntakeCandidate candidate = candidate(id);
        if (candidate.getPromotedArticleId() != null) {
            return articleRepository.findById(candidate.getPromotedArticleId())
                    .map(article -> promotionWorkflowService.attach(candidate.getId(), candidate.getHumanStatus(), article))
                    .orElseGet(() -> promotionWorkflowService.attach(candidate.getId(), candidate.getHumanStatus(), null));
        }
        if (!IntakeEnums.HUMAN_PENDING.equals(candidate.getHumanStatus())
                && !IntakeEnums.HUMAN_SAVED_FOR_LATER.equals(candidate.getHumanStatus())) {
            throw new IllegalArgumentException("Candidate cannot be promoted from status: " + candidate.getHumanStatus());
        }
        Source source = sourceRepository.findById(candidate.getSourceId())
                .orElseThrow(() -> new IllegalArgumentException("Source not found: " + candidate.getSourceId()));
        RawItem item = new RawItem(
                firstNonBlank(candidate.getChineseTitle(), candidate.getOriginalTitle()),
                candidate.getOriginalUrl(),
                candidate.getPublishedAt(),
                firstNonBlank(candidate.getDecisionSummary(), candidate.getOriginalSummary()),
                firstNonBlank(candidate.getOriginalBody(), candidate.getOriginalSummary()));
        item.withExtraction(firstNonBlank(candidate.getContentType(), "ARTICLE"),
                firstNonBlank(candidate.getExtractionMethod(), "intake:promoted"),
                candidate.getExtractionQualityScore(),
                "Intake 人工 Promote 后入文章库");
        ArticleIngestResult result = articleIngestCoordinator.ingest(source, item, progress);
        Long articleId = result.getArticle().getId();
        candidateRepository.markPromoted(id, articleId);
        IntakeCandidate promoted = candidate(id);
        return promotionWorkflowService.attach(promoted.getId(), promoted.getHumanStatus(), result.getArticle());
    }

    private IntakeCandidate toCandidate(FetchBatch batch, Source source, RawItem item) {
        IntakeCandidate candidate = new IntakeCandidate();
        candidate.setBatchId(batch.getId());
        candidate.setSourceId(source.getId());
        candidate.setSourceName(source.getName());
        candidate.setSourceType(source.getType());
        candidate.setOriginalTitle(item.getTitle());
        candidate.setOriginalUrl(item.getUrl());
        candidate.setOriginalSummary(item.getSummary());
        candidate.setOriginalBody(item.getBody());
        candidate.setContentType(item.getContentType());
        candidate.setExtractionMethod(item.getExtractionMethod());
        candidate.setExtractionQualityScore(item.getQualityScore());
        candidate.setPublishedAt(item.getPublishedAt());
        candidate.setFetchedAt(LocalDateTime.now());
        candidate.setHumanStatus(IntakeEnums.HUMAN_PENDING);
        candidate.setAgentStatus(IntakeEnums.AGENT_PENDING);
        candidate.setUrlFingerprint(fingerprintService.urlFingerprint(item.getUrl()));
        candidate.setTitleFingerprint(fingerprintService.normalizeText(item.getTitle()));
        candidate.setBodyFingerprint(String.valueOf(fingerprintService.bodySimhash(firstNonBlank(item.getBody(), item.getSummary(), item.getTitle()))));
        return candidate;
    }

    private void applyReview(IntakeCandidate candidate, CandidateReviewAgent.ReviewResult reviewResult) {
        CandidateReview review = reviewResult.getReview();
        candidate.setChineseTitle(review.getChineseTitle());
        candidate.setDecisionSummary(review.getDecisionSummary());
        candidate.setKeyFactsJson(toJson(review.getKeyFacts()));
        candidate.setWhyItMatters(review.getWhyItMatters());
        candidate.setNoveltyJudgment(review.getNoveltyJudgment());
        candidate.setRiskFlagsJson(toJson(review.getRiskFlags()));
        candidate.setAgentScore(review.getScore());
        candidate.setAgentRecommendation(review.getRecommendation());
        candidate.setAgentReason(review.getReason());
        candidate.setAgentModel(reviewResult.getModel());
        candidate.setAgentStatus(reviewResult.getStatus());
        candidate.setAgentErrorMessage(reviewResult.getErrorMessage());
        candidate.setAgentReviewJson(candidateReviewAgent.reviewJson(review));
    }

    private String scheduledSummaryJson(String scheduleSlot, BatchSummaryAgent.BatchSummary summary) {
        Map<String, String> payload = new LinkedHashMap<String, String>();
        payload.put("slot", scheduleSlot);
        payload.put("summaryText", summary.getSummaryText());
        return toJson(payload);
    }

    private boolean isDuplicate(IntakeCandidate candidate) {
        if (candidate.getUrlFingerprint() == null || candidate.getUrlFingerprint().trim().isEmpty()) {
            return false;
        }
        java.util.Optional<IntakeCandidate> duplicate = candidateRepository.findByUrlFingerprint(candidate.getUrlFingerprint());
        if (duplicate.isPresent()) {
            candidate.setDuplicateOfCandidateId(duplicate.get().getId());
            return true;
        }
        for (ArticleRepository.ArticleRecord article : articleRepository.findRecentRecords(200)) {
            if (candidate.getUrlFingerprint().equals(article.getUrlFingerprint())) {
                candidate.setDuplicateOfArticleId(article.getId());
                return true;
            }
        }
        return false;
    }

    private List<RawItem> filterByLookback(List<RawItem> items) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(LOOKBACK_DAYS);
        List<RawItem> filtered = new ArrayList<RawItem>();
        for (RawItem item : items) {
            if (item.getPublishedAt() == null || !item.getPublishedAt().isBefore(cutoff)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    private List<RawItem> limit(List<RawItem> items, int max) {
        if (items.size() <= max) {
            return items;
        }
        return new ArrayList<RawItem>(items.subList(0, max));
    }

    private int maxItems(Source source) {
        return source.getMaxItemsPerRun() <= 0 ? 10 : source.getMaxItemsPerRun();
    }

    private int lowValueCount(List<IntakeCandidate> candidates) {
        int count = 0;
        for (IntakeCandidate candidate : candidates) {
            if (IntakeEnums.AGENT_LOW_VALUE.equals(candidate.getAgentRecommendation())) {
                count++;
            }
        }
        return count;
    }

    private List<RawItem> emptyIfNull(List<RawItem> items) {
        return items == null ? Collections.<RawItem>emptyList() : items;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private String blankDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private void phase(Consumer<TaskPhase> progress, TaskPhase phase) {
        if (progress != null) progress.accept(phase);
    }
}
