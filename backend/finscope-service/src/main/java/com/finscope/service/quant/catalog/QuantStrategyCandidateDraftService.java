package com.finscope.service.quant.catalog;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.quant.QuantStrategyCatalogRepository;
import com.finscope.domain.quant.catalog.QuantStrategyCandidate;
import com.finscope.domain.quant.strategy.QuantStrategyDraft;
import com.finscope.service.quant.strategy.QuantStrategyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import com.finscope.common.exception.BizErrorCode;

@Service
public class QuantStrategyCandidateDraftService {
    private final QuantStrategyCatalogRepository repository;
    private final QuantStrategyService strategies;

    public QuantStrategyCandidateDraftService(QuantStrategyCatalogRepository repository,
                                              QuantStrategyService strategies) {
        this.repository = repository;
        this.strategies = strategies;
    }

    @Transactional
    public QuantStrategyDraft generate(Long candidateId, Long datasetId) {
        if (datasetId == null) throw new BusinessException(BizErrorCode.DATASET_REQUIRED);
        QuantStrategyCandidate candidate = repository.findById(candidateId).orElseThrow(() ->
                new BusinessException(BizErrorCode.STRATEGY_CANDIDATE_NOT_FOUND));
        if (candidate.isArchived()) throw new BusinessException(BizErrorCode.STRATEGY_CANDIDATE_ARCHIVED);
        if ("UNSUPPORTED".equals(candidate.getCompatibilityStatus())) {
            throw new BusinessException(BizErrorCode.BACKTEST_ENGINE_UNSUPPORTED_CANDIDATE);
        }
        QuantStrategyDraft draft = strategies.generateDraft(datasetId, prompt(candidate));
        repository.saveOrigin(candidateId, draft.getId(), candidate.getSourceCommitSha(), LocalDateTime.now());
        return draft;
    }

    private String prompt(QuantStrategyCandidate candidate) {
        return "请将下面的外部研究候选改写为 FinScope 可验证策略，不要声称忠实复现，也不要沿用来源收益数字。\n"
                + "来源标题：" + candidate.getTitle() + "\n"
                + "来源论文：" + text(candidate.getPaperUrl()) + "\n"
                + "来源实现：" + text(candidate.getImplementationUrl()) + "\n"
                + "本地兼容状态：" + candidate.getCompatibilityStatus() + "\n"
                + "允许使用的映射因子：" + String.join(",", candidate.getMappedFactors()) + "\n"
                + "适配说明：" + candidate.getAdaptationNote() + "\n"
                + "来源指标仅作线索，不得写入投资假设或风险边界；必须明确这是面向 A 股多头 Top-N 的本地适配版本。";
    }

    private String text(String value) { return value == null || value.trim().isEmpty() ? "未提供" : value; }
}
