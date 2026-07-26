package com.finscope.service.financials;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.financials.FinancialInterpretation;
import com.finscope.rpc.llm.LlmChatClient;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FinancialInterpretationAgent {
    private final LlmChatClient llm;
    private final ObjectMapper json;
    private final FinancialInterpretationResponseParser parser;
    private final FinancialInterpretationGate gate;
    private final FinancialInterpretationFallbackBuilder fallback;

    public FinancialInterpretationAgent(LlmChatClient llm, ObjectMapper json,
                                        FinancialInterpretationResponseParser parser,
                                        FinancialInterpretationGate gate,
                                        FinancialInterpretationFallbackBuilder fallback) {
        this.llm = llm;
        this.json = json;
        this.parser = parser;
        this.gate = gate;
        this.fallback = fallback;
    }

    public FinancialInterpretation interpret(FinancialEvidencePacket packet) {
        return interpretWithMetrics(packet).getValue();
    }

    public Execution interpretWithMetrics(FinancialEvidencePacket packet) {
        if (!llm.isConfigured()) {
            return new Execution(fallback(packet, "LLM_NOT_CONFIGURED", new ArrayList<String>()), 0);
        }
        List<String> errors = new ArrayList<String>();
        String output = null;
        int llmCallCount = 0;
        try {
            llmCallCount++;
            output = llm.complete(systemPrompt(), modelPayload(packet));
            try {
                return new Execution(success(packet, output, "LLM", errors), llmCallCount);
            } catch (IllegalArgumentException first) {
                errors.add("首次输出：" + message(first));
            }
            llmCallCount++;
            output = llm.complete(repairPrompt(), repairInput(packet, output, errors.get(0)));
            try {
                return new Execution(success(packet, output, "REPAIRED", errors), llmCallCount);
            } catch (IllegalArgumentException second) {
                errors.add("修复输出：" + message(second));
                return new Execution(fallback(packet, "OUTPUT_REJECTED_BY_GATE", errors), llmCallCount);
            }
        } catch (SocketTimeoutException error) {
            errors.add("模型调用超时：" + message(error));
            return new Execution(fallback(packet, "LLM_TIMEOUT", errors), llmCallCount);
        } catch (Exception error) {
            errors.add("模型调用失败：" + message(error));
            return new Execution(fallback(packet, "LLM_UNAVAILABLE", errors), llmCallCount);
        }
    }

    public static final class Execution {
        private final FinancialInterpretation value;
        private final int llmCallCount;

        public Execution(FinancialInterpretation value, int llmCallCount) {
            this.value = value;
            this.llmCallCount = llmCallCount;
        }

        public FinancialInterpretation getValue() {
            return value;
        }

        public int getLlmCallCount() {
            return llmCallCount;
        }
    }

    public String modelName() {
        return llm.modelName();
    }

    private FinancialInterpretation success(FinancialEvidencePacket packet, String output,
                                            String mode, List<String> priorErrors) {
        try {
            JsonNode root = parser.parse(output);
            FinancialInterpretation.Result accepted = gate.apply(root, packet);
            FinancialInterpretation value = base(packet);
            value.setStatus("SUCCESS");
            value.setGenerationMode(mode);
            value.setResult(accepted);
            value.setValidationErrors(new ArrayList<String>(priorErrors));
            return value;
        } catch (Exception error) {
            throw error instanceof IllegalArgumentException
                    ? (IllegalArgumentException) error
                    : new IllegalArgumentException(message(error), error);
        }
    }

    private FinancialInterpretation fallback(FinancialEvidencePacket packet, String reason,
                                             List<String> errors) {
        FinancialInterpretation value = fallback.build(packet, reason);
        value.setModelName(llm.modelName());
        value.setValidationErrors(new ArrayList<String>(errors));
        value.setFailureMessage(errors.isEmpty() ? reason : errors.get(errors.size() - 1));
        return value;
    }

    private FinancialInterpretation base(FinancialEvidencePacket packet) {
        FinancialInterpretation value = new FinancialInterpretation();
        value.setReportId(packet.getReportId());
        value.setPromptVersion(packet.getPromptVersion());
        value.setModelName(llm.modelName());
        return value;
    }

    private String repairInput(FinancialEvidencePacket packet, String invalidOutput,
                               String validationError) throws Exception {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("validationError", validationError);
        value.put("invalidOutput", shorten(invalidOutput, 8000));
        value.put("evidencePacket", json.readTree(modelPayload(packet)));
        return json.writeValueAsString(value);
    }

    private String systemPrompt() {
        return "你是A股非金融企业财报解读Agent。只能使用evidence中的证据和数字，只能引用现有id；" +
                "输出单个JSON对象，字段为operatingState、confidence、executiveSummary、periodChanges、" +
                "crossStatementInsights、dimensions、positiveSignals、risks、turningPoints、watchpoints、" +
                "limitations、disclaimer。所有Claim必须包含claim、claimType、refs；executiveSummary必须是数组，输出3条；" +
                "periodChanges输出2至3条最重要的同比、环比或连续趋势，证据不足可为空；" +
                "crossStatementInsights输出1至3条利润表、资产负债表、现金流量表之间的联动观察，证据不足可为空；" +
                "positiveSignals、risks、turningPoints、watchpoints必须是数组；积极信号和风险各不超过3条，拐点和观察项各不超过2条。" +
                "dimensions必须是数组，元素必须包含code、assessment、summary、refs、details；" +
                "details为1至2条Claim，分别说明最关键的事实、趋势、驱动或反证。" +
                "limitations必须是字符串数组，disclaimer必须是字符串。" +
                "operatingState只能是IMPROVING、STABLE、UNDER_PRESSURE、INSUFFICIENT_EVIDENCE之一；" +
                "confidence只能是HIGH、MEDIUM、LOW之一；每个dimension的assessment只能是POSITIVE、" +
                "NEUTRAL、NEGATIVE、INSUFFICIENT_EVIDENCE之一。" +
                "每条实质结论必须有refs；dimensions必须完整覆盖GROWTH、PROFITABILITY、" +
                "EARNINGS_QUALITY、CASH_QUALITY、ASSET_QUALITY、SOLVENCY_CAPITAL_DISCIPLINE。" +
                "优先描述方向、关系和经营含义，页面会通过refs展示精确数值；除非结论必须精确量化，否则不要复述数字。" +
                "不得出现evidence未原样提供的数字，包括自行计算的比率、倍数、天数、预测值和序号。" +
                "不得重新计算、创造数字、给出买卖建议、目标价或收益承诺；事实、推断和观察项分别标记" +
                "FACT、INFERENCE、WATCHPOINT；原因和影响必须标为INFERENCE；单条claim不超过80个中文字符，" +
                "维度summary不超过60个中文字符；数据不足时使用INSUFFICIENT_EVIDENCE。只返回JSON。";
    }

    private String repairPrompt() {
        return systemPrompt() + "你正在修复未通过服务端门禁的输出，必须纠正validationError，只返回修复后的JSON。";
    }

    private String message(Throwable error) {
        String value = error == null || error.getMessage() == null
                ? "未知错误" : error.getMessage().replace('\n', ' ').replace('\r', ' ').trim();
        return shorten(value, 500);
    }

    private String shorten(String value, int limit) {
        if (value == null) return "";
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    private String modelPayload(FinancialEvidencePacket packet) {
        return packet.getModelPayloadJson() == null || packet.getModelPayloadJson().trim().isEmpty()
                ? packet.getPayloadJson() : packet.getModelPayloadJson();
    }
}
