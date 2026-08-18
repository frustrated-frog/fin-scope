package com.finscope.service.quant.strategy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.quant.QuantStrategyRepository;
import com.finscope.dao.quant.QuantStrategyCatalogRepository;
import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.domain.quant.strategy.QuantStrategyDraft;
import com.finscope.domain.quant.strategy.QuantStrategySpec;
import com.finscope.domain.quant.strategy.QuantStrategyVersion;
import com.finscope.service.quant.data.QuantDatasetService;
import com.finscope.service.quant.factor.FactorRegistry;
import com.finscope.service.factorresearch.FactorProviderRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import com.finscope.common.exception.BizErrorCode;

@Service
public class QuantStrategyService {
    public static final String ENGINE_VERSION = "quant-java-v1";
    @Resource private QuantStrategyRepository repository;
    @Resource private QuantDatasetService datasets;
    @Resource private QuantStrategyAgent agent;
    @Resource private FactorRegistry factors;
    @Resource private FactorProviderRegistry factorProviders;
    @Resource private QuantStrategyCatalogRepository catalogRepository;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    @Transactional
    public QuantStrategyDraft generateDraft(Long datasetId, String prompt) {
        if (datasetId == null) throw new BusinessException(BizErrorCode.DATASET_REQUIRED);
        if (prompt == null || prompt.trim().isEmpty()) throw new BusinessException(BizErrorCode.STRATEGY_PROMPT_REQUIRED);
        QuantDataset dataset = datasets.get(datasetId);
        if (!"READY".equals(dataset.getStatus())) {
            throw new BusinessException(BizErrorCode.DATASET_QUALITY_GATE_PENDING);
        }
        java.util.Set<String> availableFactors = datasets.availableFactorCodes(datasetId);
        QuantStrategyDraft draft = agent.generate(datasetId, prompt, availableFactors, dataset.getStartDate(), dataset.getEndDate());
        draft.setValidatedDatasetFingerprint(dataset.getFingerprint());
        if ("VALIDATED".equals(draft.getStatus())) {
            if (draft.getSpec().getStartDate() == null && draft.getSpec().getEndDate() == null) {
                draft.getSpec().setStartDate(dataset.getStartDate()); draft.getSpec().setEndDate(dataset.getEndDate());
            }
            if (draft.getSpec().getStartDate().isBefore(dataset.getStartDate()) || draft.getSpec().getEndDate().isAfter(dataset.getEndDate())) {
                draft.setStatus("FAILED"); draft.setValidationIssues(java.util.Collections.singletonList("回测日期必须位于数据集区间内"));
            }
            try { draft.setNormalizedSpec(mapper.writeValueAsString(draft.getSpec())); }
            catch (Exception ex) { throw new BusinessException(BizErrorCode.STRATEGY_DATE_LOCK_FAILED, ex); }
        }
        if ("VALIDATED".equals(draft.getStatus()) && draft.getSpec().getFactors().stream()
                .anyMatch(item -> !availableFactors.contains(item.getCode()))) {
            draft.setStatus("FAILED");
            draft.setValidationIssues(java.util.Collections.singletonList("策略使用了当前数据集覆盖不足的因子"));
        }
        return repository.saveDraft(draft);
    }

    @Transactional
    public QuantStrategyVersion confirm(Long draftId) {
        QuantStrategyDraft draft = repository.findDraft(draftId).orElseThrow(() ->
                new BusinessException(BizErrorCode.STRATEGY_DRAFT_NOT_FOUND));
        if (!"VALIDATED".equals(draft.getStatus())) {
            throw new BusinessException(BizErrorCode.STRATEGY_DRAFT_MUST_PASS_VALIDATION);
        }
        try {
            QuantStrategySpec spec = mapper.readValue(draft.getNormalizedSpec(), QuantStrategySpec.class);
            QuantDataset dataset = datasets.get(spec.getDatasetId());
            if (!"READY".equals(dataset.getStatus())) throw new BusinessException(BizErrorCode.DATASET_QUALITY_GATE_FAILED);
            if (!text(dataset.getFingerprint())) throw new BusinessException(BizErrorCode.DATASET_FINGERPRINT_MISSING);
            if (!dataset.getFingerprint().equals(draft.getValidatedDatasetFingerprint()))
                throw new BusinessException(BizErrorCode.DATASET_CHANGED_AFTER_DRAFT);
            new QuantStrategySpecValidator(factors, factorProviders).validateOrThrow(spec);
            java.util.Set<String> available = datasets.availableFactorCodes(dataset.getId());
            if (spec.getFactors().stream().anyMatch(item -> !available.contains(item.getCode())))
                throw new BusinessException(BizErrorCode.STRATEGY_COVERAGE_STALE);
            if (spec.getStartDate() == null || spec.getEndDate() == null || spec.getStartDate().isBefore(dataset.getStartDate())
                    || spec.getEndDate().isAfter(dataset.getEndDate()))
                throw new BusinessException(BizErrorCode.BACKTEST_DATE_OUT_OF_RANGE);
            QuantStrategyVersion value = new QuantStrategyVersion();
            value.setName(spec.getName()); value.setDatasetId(spec.getDatasetId());
            value.setVersion(repository.nextVersion(spec.getName())); value.setSpecJson(draft.getNormalizedSpec());
            value.setDatasetFingerprint(dataset.getFingerprint()); value.setEngineVersion(ENGINE_VERSION);
            value.setStrategyFingerprint(sha256(draft.getNormalizedSpec() + "|" + dataset.getFingerprint() + "|" + ENGINE_VERSION));
            boolean catalogOrigin = catalogRepository != null && catalogRepository.findCandidateIdByDraft(draftId).isPresent();
            value.setSource(catalogOrigin ? "CATALOG_AGENT" : "AGENT");
            QuantStrategyVersion saved = repository.saveVersion(value);
            if (catalogOrigin) catalogRepository.linkVersionForDraft(draftId, saved.getId());
            return saved;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(BizErrorCode.STRATEGY_VERSION_CREATE_FAILED, ex);
        }
    }

    public List<QuantStrategyVersion> listVersions() { return repository.findVersions(); }
    public QuantStrategyDraft getDraft(Long id) {
        return repository.findDraft(id).orElseThrow(() ->
                new BusinessException(BizErrorCode.STRATEGY_DRAFT_NOT_FOUND));
    }
    @Transactional
    public void recordDraftFailure(Long id, String issue) {
        repository.markDraftFailed(id, issue);
    }
    public QuantStrategyVersion getVersion(Long id) {
        return repository.findVersion(id).orElseThrow(() ->
                new BusinessException(BizErrorCode.STRATEGY_VERSION_NOT_FOUND));
    }

    private String sha256(String input) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte item : digest) result.append(String.format("%02x", item & 0xff));
        return result.toString();
    }
    private boolean text(String value) { return value != null && !value.trim().isEmpty(); }
}
