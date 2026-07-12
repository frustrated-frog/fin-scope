package com.finscope.service.quant.experiment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.quant.QuantExperimentRepository;
import com.finscope.domain.quant.backtest.BacktestMetrics;
import com.finscope.domain.quant.experiment.QuantExperiment;
import com.finscope.rpc.llm.LlmChatClient;
import org.springframework.stereotype.Component;

@Component
public class QuantExperimentAgent {
    private final LlmChatClient llm;
    private final QuantExperimentRepository repository;
    private final ObjectMapper mapper = new ObjectMapper();

    public QuantExperimentAgent(LlmChatClient llm, QuantExperimentRepository repository) {
        this.llm = llm; this.repository = repository;
    }

    public String interpret(QuantExperiment experiment) {
        if (experiment == null || !"SUCCEEDED".equals(experiment.getStatus()) || experiment.getResult() == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有成功实验才能请求 Agent 解读");
        }
        if (!llm.isConfigured()) throw new BusinessException(ErrorCode.BAD_REQUEST, "实验解读 Agent 尚未配置");
        try {
            String raw = llm.complete(systemPrompt(), metricSummary(experiment.getResult().getMetrics()));
            String json = extractJson(raw); JsonNode root = mapper.readTree(json);
            if (!root.isObject() || root.size() != 3) throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent 解读字段不符合协议");
            requireArray(root, "observations"); requireArray(root, "risks"); requireArray(root, "nextExperiments");
            String normalized = mapper.writeValueAsString(root);
            repository.saveInterpretation(experiment.getId(), normalized, llm.modelName()); return normalized;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR, "实验解读 Agent 返回内容无法通过校验", ex);
        }
    }

    private String systemPrompt() {
        return "你是量化实验审阅 Agent。只能根据用户消息中的服务端指标做定性解读，不得生成新指标、收益承诺或交易指令。"
                + "只输出 JSON：observations、risks、nextExperiments 三个字符串数组。每个下一实验只能改变一个主要变量，且不会自动执行。";
    }
    private String metricSummary(BacktestMetrics m) throws Exception {
        java.util.Map<String,Object> values = new java.util.LinkedHashMap<String,Object>();
        values.put("totalReturn", m.getTotalReturn()); values.put("annualizedReturn", m.getAnnualizedReturn());
        values.put("maxDrawdown", m.getMaxDrawdown()); values.put("sharpe", m.getSharpeRatio());
        values.put("turnover", m.getTurnover()); values.put("benchmarkReturn", m.getBenchmarkReturn());
        values.put("excessReturn", m.getExcessReturn()); values.put("tradeCount", m.getTradeCount());
        return mapper.writeValueAsString(values);
    }
    private void requireArray(JsonNode root, String field) {
        JsonNode values = root.path(field);
        if (!values.isArray() || values.size() < 1 || values.size() > 8) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent 解读字段数量不合规：" + field);
        }
        for (JsonNode value : values) {
            if (!value.isTextual() || value.textValue().trim().isEmpty() || value.textValue().length() > 300) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent 解读必须是非空短文本：" + field);
            }
        }
    }
    private String extractJson(String value) {
        int start = value == null ? -1 : value.indexOf('{'); int end = value == null ? -1 : value.lastIndexOf('}');
        if (start < 0 || end <= start) throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent 解读不是合法 JSON");
        return value.substring(start, end + 1);
    }
}
