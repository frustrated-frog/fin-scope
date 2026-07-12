package com.finscope.service.quant.experiment;

import com.finscope.dao.quant.QuantExperimentRepository;
import com.finscope.domain.quant.backtest.BacktestResult;
import com.finscope.domain.quant.experiment.QuantExperiment;
import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.finscope.common.exception.BusinessException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class QuantExperimentAgentTest {
    @Test
    void storesStructuredInterpretationWithoutStartingAnotherExperiment() {
        QuantExperimentRepository repository = mock(QuantExperimentRepository.class);
        LlmChatClient llm = new LlmChatClient() {
            public boolean isConfigured() { return true; }
            public String modelName() { return "test-model"; }
            public String complete(String system, String user) {
                return "{\"observations\":[{\"metricCode\":\"MAX_DRAWDOWN\",\"assessment\":\"回撤约束仍需关注\"}],"
                        + "\"risks\":[\"样本期有限\"],\"nextExperiments\":[{\"variable\":\"REBALANCE_EVERY\","
                        + "\"change\":\"延长调仓周期\",\"rationale\":\"观察换手变化\"}]}";
            }
        };
        QuantExperiment experiment = new QuantExperiment(); experiment.setId(1L); experiment.setStatus("SUCCEEDED");
        experiment.setResult(new BacktestResult());

        String value = new QuantExperimentAgent(llm, repository).interpret(experiment);

        assertTrue(value.contains("nextExperiments"));
        verify(repository).saveInterpretation(1L, value, "test-model");
    }

    @Test
    void rejectsInterpretationThatInventsNumericClaims() {
        QuantExperimentRepository repository = mock(QuantExperimentRepository.class); LlmChatClient llm = new LlmChatClient() {
            public boolean isConfigured() { return true; } public String modelName() { return "test-model"; }
            public String complete(String system, String user) { return "{\"observations\":[{\"metricCode\":\"SHARPE\",\"assessment\":\"夏普达到9.9\"}],"
                    + "\"risks\":[\"样本有限\"],\"nextExperiments\":[{\"variable\":\"COST\",\"change\":\"提高成本\",\"rationale\":\"压力测试\"}]}"; }
        };
        QuantExperiment experiment = new QuantExperiment(); experiment.setId(1L); experiment.setStatus("SUCCEEDED"); experiment.setResult(new BacktestResult());
        // 阿拉伯数字和全角数字都不能进入解释文本，服务端数值只在指标区展示。
        assertThrows(BusinessException.class, () -> new QuantExperimentAgent(llm,repository).interpret(experiment));
    }
}
