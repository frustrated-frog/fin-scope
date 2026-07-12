package com.finscope.service.quant.strategy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.quant.QuantStrategyRepository;
import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.domain.quant.strategy.QuantStrategyDraft;
import com.finscope.domain.quant.strategy.QuantStrategySpec;
import com.finscope.domain.quant.strategy.QuantStrategyVersion;
import com.finscope.service.quant.data.QuantDatasetService;
import com.finscope.service.quant.factor.FactorRegistry;
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
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    @Transactional
    public QuantStrategyDraft generateDraft(Long datasetId, String prompt) {
        if (datasetId == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "数据集不能为空");
        if (prompt == null || prompt.trim().isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "策略描述不能为空");
        QuantDataset dataset = datasets.get(datasetId);
        if (!"READY".equals(dataset.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "数据集尚未通过质量门禁");
        }
        QuantStrategyDraft draft = agent.generate(datasetId, prompt);
        if ("VALIDATED".equals(draft.getStatus()) && requiresFundamentals(draft.getSpec()) && !datasets.hasFundamentals(datasetId)) {
            draft.setStatus("FAILED");
            draft.setValidationIssues(java.util.Collections.singletonList("策略使用财务因子，但当前数据集没有可用的时点财务数据"));
        }
        return repository.saveDraft(draft);
    }

    @Transactional
    public QuantStrategyVersion confirm(Long draftId) {
        QuantStrategyDraft draft = repository.findDraft(draftId).orElseThrow(() ->
                new BusinessException(ErrorCode.NOT_FOUND, "策略草案不存在"));
        if (!"VALIDATED".equals(draft.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有通过校验的策略草案才能确认");
        }
        try {
            QuantStrategySpec spec = mapper.readValue(draft.getNormalizedSpec(), QuantStrategySpec.class);
            QuantDataset dataset = datasets.get(spec.getDatasetId());
            if (!text(dataset.getFingerprint())) {
                throw new BusinessException(ErrorCode.CONFLICT, "数据集缺少可复现指纹");
            }
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
                new BusinessException(ErrorCode.NOT_FOUND, "策略版本不存在"));
    }

    private String sha256(String input) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte item : digest) result.append(String.format("%02x", item & 0xff));
        return result.toString();
    }
    private boolean text(String value) { return value != null && !value.trim().isEmpty(); }
    private boolean requiresFundamentals(QuantStrategySpec spec) {
        return spec != null && spec.getFactors() != null && spec.getFactors().stream()
                .anyMatch(item -> factors.get(item.getCode()).isPointInTime());
    }
}
