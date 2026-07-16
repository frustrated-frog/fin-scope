package com.finscope.service.quant.experiment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.quant.QuantExperimentRepository;
import com.finscope.domain.quant.backtest.BacktestMetrics;
import com.finscope.domain.quant.backtest.BacktestResult;
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
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "只有成功实验才能请求 Agent 解读");
        }
        if (!llm.isConfigured()) throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "实验解读 Agent 尚未配置");
        try {
            String raw = llm.complete(systemPrompt(), metricSummary(experiment.getResult()));
            String json = extractJson(raw); JsonNode root = mapper.readTree(json);
            if (!root.isObject() || root.size() != 3) throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "Agent 解读字段不符合协议");
            requireObservations(root.path("observations")); requireStringArray(root.path("risks"), "risks");
            requireNextExperiments(root.path("nextExperiments"));
            String normalized = mapper.writeValueAsString(root);
            repository.saveInterpretation(experiment.getId(), normalized, llm.modelName()); return normalized;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "实验解读 Agent 返回内容无法通过校验", ex);
        }
    }

    private String systemPrompt() {
        return "你是量化实验审阅 Agent。只能根据用户消息中的服务端指标、年度表现与告警做定性解读，不得复述或生成任何数字、收益承诺或交易指令。"
                + "只输出 JSON。observations 为对象数组，每项严格包含 metricCode 和 assessment；metricCode 只能取服务端指标代码。"
                + "risks 为不含数字的字符串数组。nextExperiments 为对象数组，每项严格包含 variable、change、rationale，"
                + "variable 只能是 FACTORS、TOP_N、REBALANCE_EVERY、COST、FILTERS 之一，每项只改变该一个变量，所有文本不得含数字。";
    }
    private String metricSummary(BacktestResult result) throws Exception {
        BacktestMetrics m = result.getMetrics();
        java.util.Map<String,Object> values = new java.util.LinkedHashMap<String,Object>();
        values.put("TOTAL_RETURN", m.getTotalReturn()); values.put("ANNUAL_RETURN", m.getAnnualizedReturn());
        values.put("MAX_DRAWDOWN", m.getMaxDrawdown()); values.put("SHARPE", m.getSharpeRatio());
        values.put("TURNOVER", m.getTurnover()); values.put("BENCHMARK_RETURN", m.getBenchmarkReturn());
        values.put("EXCESS_RETURN", m.getExcessReturn()); values.put("TRADE_COUNT", m.getTradeCount());
        values.put("annualPerformance", result.getAnnualPerformance()); values.put("warnings", result.getWarnings());
        return mapper.writeValueAsString(values);
    }
    private void requireStringArray(JsonNode values, String field) {
        if (!values.isArray() || values.size() < 1 || values.size() > 8) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "Agent 解读字段数量不合规：" + field);
        }
        for (JsonNode value : values) {
            if (!safeText(value)) {
                throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "Agent 解读必须是非空短文本：" + field);
            }
        }
    }
    private void requireObservations(JsonNode values) {
        if (!values.isArray() || values.size() < 1 || values.size() > 8) throw bad("observations");
        java.util.Set<String> allowed = new java.util.HashSet<String>(java.util.Arrays.asList(
                "TOTAL_RETURN","ANNUAL_RETURN","MAX_DRAWDOWN","SHARPE","TURNOVER","BENCHMARK_RETURN","EXCESS_RETURN","TRADE_COUNT"));
        for (JsonNode value : values) {
            if (!value.isObject() || value.size() != 2 || !allowed.contains(value.path("metricCode").asText()) || !safeText(value.path("assessment"))) throw bad("observations");
        }
    }
    private void requireNextExperiments(JsonNode values) {
        if (!values.isArray() || values.size() < 1 || values.size() > 8) throw bad("nextExperiments");
        java.util.Set<String> allowed = new java.util.HashSet<String>(java.util.Arrays.asList("FACTORS","TOP_N","REBALANCE_EVERY","COST","FILTERS"));
        for (JsonNode value : values) {
            if (!value.isObject() || value.size() != 3 || !allowed.contains(value.path("variable").asText())
                    || !safeText(value.path("change")) || !safeText(value.path("rationale"))) throw bad("nextExperiments");
        }
    }
    private boolean safeText(JsonNode value) {
        return value.isTextual() && !value.textValue().trim().isEmpty() && value.textValue().length() <= 300
                && !value.textValue().matches(".*[0-9０-９].*");
    }
    private BusinessException bad(String field) { return new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "Agent 解读字段不符合协议：" + field); }
    private String extractJson(String value) {
        int start = value == null ? -1 : value.indexOf('{'); int end = value == null ? -1 : value.lastIndexOf('}');
        if (start < 0 || end <= start) throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "Agent 解读不是合法 JSON");
        return value.substring(start, end + 1);
    }
}
