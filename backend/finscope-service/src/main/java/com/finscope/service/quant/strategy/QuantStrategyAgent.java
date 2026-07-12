package com.finscope.service.quant.strategy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.quant.factor.FactorDefinition;
import com.finscope.domain.quant.strategy.QuantStrategyDraft;
import com.finscope.domain.quant.strategy.QuantStrategySpec;
import com.finscope.rpc.llm.LlmChatClient;
import com.finscope.service.quant.factor.FactorRegistry;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Component
public class QuantStrategyAgent {
    private final LlmChatClient llm;
    private final FactorRegistry registry;
    private final QuantStrategySpecValidator validator;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    public QuantStrategyAgent(LlmChatClient llm, FactorRegistry registry) {
        this(llm, registry, new QuantStrategySpecValidator(registry));
    }

    public QuantStrategyAgent(LlmChatClient llm, FactorRegistry registry, QuantStrategySpecValidator validator) {
        this.llm = llm; this.registry = registry; this.validator = validator;
    }

    public QuantStrategyDraft generate(Long datasetId, String prompt) {
        if (!llm.isConfigured()) throw new BusinessException(ErrorCode.BAD_REQUEST, "策略 Agent 尚未配置");
        if (prompt == null || prompt.trim().isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "策略描述不能为空");
        try {
            String raw = llm.complete(systemPrompt(datasetId), prompt.trim());
            QuantStrategySpec spec = mapper.readValue(extractJson(raw), QuantStrategySpec.class);
            if (!datasetId.equals(spec.getDatasetId())) throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent 返回的数据集不匹配");
            validator.validateOrThrow(spec);
            QuantStrategyDraft draft = new QuantStrategyDraft();
            draft.setDatasetId(datasetId); draft.setPrompt(prompt.trim()); draft.setRawResponse(raw);
            draft.setSpec(spec); draft.setNormalizedSpec(mapper.writeValueAsString(spec));
            draft.setStatus("VALIDATED"); draft.setModel(llm.modelName()); draft.setCreatedAt(LocalDateTime.now());
            return draft;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR, "策略 Agent 返回内容无法通过结构化校验", ex);
        }
    }

    private String systemPrompt(Long datasetId) {
        String catalog = registry.list().stream().map(FactorDefinition::getCode).collect(Collectors.joining(","));
        return "你是量化策略研究 Agent。只输出一个 JSON 对象，不要输出收益预测或代码。datasetId 必须为 " + datasetId
                + "。可用因子仅限：" + catalog + "。策略必须为 Top-N 等权、收盘后生成信号、NEXT_OPEN 执行。"
                + "字段严格为 name,datasetId,benchmark,investmentHypothesis,riskBoundary,factors,portfolio,filters,execution,cost。"
                + "因子权重之和为1；portfolio 包含 topN,rebalanceEvery,weighting；filters 包含 excludeSt,minTradingDays,minAmount；"
                + "execution 包含 signalPrice,fillPrice,slippageBps；cost 包含 buyCommission,sellCommission,stampDuty,minimumCommission。";
    }

    private String extractJson(String raw) {
        if (raw == null) throw new IllegalArgumentException("empty response");
        int start = raw.indexOf('{'); int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IllegalArgumentException("no json object");
        return raw.substring(start, end + 1);
    }
}
