package com.finscope.service.quant.experiment;

import com.finscope.dao.quant.QuantExperimentRepository;
import com.finscope.domain.quant.backtest.BacktestResult;
import com.finscope.domain.quant.experiment.QuantExperiment;
import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
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
                return "{\"observations\":[\"收益伴随较高回撤\"],\"risks\":[\"样本期有限\"],"
                        + "\"nextExperiments\":[\"仅延长调仓周期，其余参数保持不变\"]}";
            }
        };
        QuantExperiment experiment = new QuantExperiment(); experiment.setId(1L); experiment.setStatus("SUCCEEDED");
        experiment.setResult(new BacktestResult());

        String value = new QuantExperimentAgent(llm, repository).interpret(experiment);

        assertTrue(value.contains("nextExperiments"));
        verify(repository).saveInterpretation(1L, value, "test-model");
    }
}
