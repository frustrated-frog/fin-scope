package com.finscope.service.quant.strategy;

import com.finscope.common.exception.BusinessException;
import com.finscope.domain.quant.strategy.QuantStrategyDraft;
import com.finscope.rpc.llm.LlmChatClient;
import com.finscope.service.quant.factor.FactorRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuantStrategyAgentTest {
    @Test
    void extractsAndValidatesStructuredDraft() {
        LlmChatClient llm = client("```json\n{\"name\":\"质量动量\",\"datasetId\":1,"
                + "\"benchmark\":\"000300.SH\",\"investmentHypothesis\":\"质量叠加趋势\","
                + "\"riskBoundary\":\"历史研究\",\"factors\":[{\"code\":\"ROE\",\"weight\":0.5,\"direction\":\"HIGH\"},"
                + "{\"code\":\"MOMENTUM_20D\",\"weight\":0.5,\"direction\":\"HIGH\"}],"
                + "\"portfolio\":{\"topN\":20,\"rebalanceEvery\":20,\"weighting\":\"EQUAL\"},"
                + "\"filters\":{\"excludeSt\":true,\"minTradingDays\":60,\"minAmount\":5000000},"
                + "\"execution\":{\"signalPrice\":\"CLOSE\",\"fillPrice\":\"NEXT_OPEN\",\"slippageBps\":10},"
                + "\"cost\":{\"buyCommission\":0.0003,\"sellCommission\":0.0003,\"stampDuty\":0.001,\"minimumCommission\":5}}\n```");
        FactorRegistry registry = new FactorRegistry();

        QuantStrategyDraft draft = new QuantStrategyAgent(llm, registry,
                new QuantStrategySpecValidator(registry)).generate(1L, "偏好质量和中期动量");

        assertEquals("VALIDATED", draft.getStatus());
        assertEquals("质量动量", draft.getSpec().getName());
        assertEquals("test-model", draft.getModel());
    }

    @Test
    void refusesToInventDraftWhenLlmIsNotConfigured() {
        FactorRegistry registry = new FactorRegistry();
        BusinessException error = assertThrows(BusinessException.class, () ->
                new QuantStrategyAgent(client(null), registry, new QuantStrategySpecValidator(registry))
                        .generate(1L, "任意策略"));
        assertEquals("策略 Agent 尚未配置", error.getMessage());
    }

    private LlmChatClient client(String response) {
        return new LlmChatClient() {
            public boolean isConfigured() { return response != null; }
            public String modelName() { return "test-model"; }
            public String complete(String systemPrompt, String userPrompt) { return response; }
        };
    }
}
