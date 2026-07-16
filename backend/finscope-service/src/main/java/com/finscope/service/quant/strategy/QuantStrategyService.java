package com.finscope.service.quant.strategy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.quant.QuantStrategyRepository;
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

@Service
public class QuantStrategyService {
    public static final String ENGINE_VERSION = "quant-java-v1";
    @Resource private QuantStrategyRepository repository;
    @Resource private QuantDatasetService datasets;
    @Resource private QuantStrategyAgent agent;
    @Resource private FactorRegistry factors;
    @Resource private FactorProviderRegistry factorProviders;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    @Transactional
    public QuantStrategyDraft generateDraft(Long datasetId, String prompt) {
        if (datasetId == null) throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "数据集不能为空");
        if (prompt == null || prompt.trim().isEmpty()) throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "策略描述不能为空");
        QuantDataset dataset = datasets.get(datasetId);
        if (!"READY".equals(dataset.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "数据集尚未通过质量门禁");
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
            catch (Exception ex) { throw new BusinessException(ErrorCode.INTERNAL_ERROR, "策略日期锁定失败", ex); }
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
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "策略草案不存在"));
        if (!"VALIDATED".equals(draft.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "只有通过校验的策略草案才能确认");
        }
        try {
            QuantStrategySpec spec = mapper.readValue(draft.getNormalizedSpec(), QuantStrategySpec.class);
            QuantDataset dataset = datasets.get(spec.getDatasetId());
            if (!"READY".equals(dataset.getStatus())) throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "数据集已不再满足质量门禁");
            if (!text(dataset.getFingerprint())) throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "数据集缺少可复现指纹");
            if (!dataset.getFingerprint().equals(draft.getValidatedDatasetFingerprint()))
                throw new BusinessException(ErrorCode.DATA_VERSION_CONFLICT, "数据集在草案生成后已变化，请重新生成策略草案");
            new QuantStrategySpecValidator(factors, factorProviders).validateOrThrow(spec);
            java.util.Set<String> available = datasets.availableFactorCodes(dataset.getId());
            if (spec.getFactors().stream().anyMatch(item -> !available.contains(item.getCode())))
                throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "数据变化后策略因子覆盖率已不足，请重新生成草案");
            if (spec.getStartDate() == null || spec.getEndDate() == null || spec.getStartDate().isBefore(dataset.getStartDate())
                    || spec.getEndDate().isAfter(dataset.getEndDate()))
                throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "策略回测日期已超出当前数据集范围");
            QuantStrategyVersion value = new QuantStrategyVersion();
            value.setName(spec.getName()); value.setDatasetId(spec.getDatasetId());
            value.setVersion(repository.nextVersion(spec.getName())); value.setSpecJson(draft.getNormalizedSpec());
            value.setDatasetFingerprint(dataset.getFingerprint()); value.setEngineVersion(ENGINE_VERSION);
            value.setStrategyFingerprint(sha256(draft.getNormalizedSpec() + "|" + dataset.getFingerprint() + "|" + ENGINE_VERSION));
            value.setSource("AGENT"); return repository.saveVersion(value);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "策略版本创建失败", ex);
        }
    }

    public List<QuantStrategyVersion> listVersions() { return repository.findVersions(); }
    public QuantStrategyVersion getVersion(Long id) {
        return repository.findVersion(id).orElseThrow(() ->
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "策略版本不存在"));
    }

    private String sha256(String input) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte item : digest) result.append(String.format("%02x", item & 0xff));
        return result.toString();
    }
    private boolean text(String value) { return value != null && !value.trim().isEmpty(); }
}
