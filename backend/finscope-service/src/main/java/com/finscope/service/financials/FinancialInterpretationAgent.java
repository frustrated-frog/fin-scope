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
    private static final int PRIMARY_TIMEOUT_MS = 60_000;
    private static final int REPAIR_TIMEOUT_MS = 30_000;

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
        if (!llm.isConfigured()) return fallback(packet, "LLM_NOT_CONFIGURED", new ArrayList<String>());
        List<String> errors = new ArrayList<String>();
        String output = null;
        try {
            output = llm.complete(systemPrompt(), packet.getPayloadJson(), PRIMARY_TIMEOUT_MS);
            try {
                return success(packet, output, "LLM", errors);
            } catch (IllegalArgumentException first) {
                errors.add("首次输出：" + message(first));
            }
            output = llm.complete(repairPrompt(), repairInput(packet, output, errors.get(0)),
                    REPAIR_TIMEOUT_MS);
            try {
                return success(packet, output, "REPAIRED", errors);
            } catch (IllegalArgumentException second) {
                errors.add("修复输出：" + message(second));
                return fallback(packet, "OUTPUT_REJECTED_BY_GATE", errors);
            }
        } catch (SocketTimeoutException error) {
            errors.add("模型调用超时：" + message(error));
            return fallback(packet, "LLM_TIMEOUT", errors);
        } catch (Exception error) {
            errors.add("模型调用失败：" + message(error));
            return fallback(packet, "LLM_UNAVAILABLE", errors);
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
        value.put("evidencePacket", json.readTree(packet.getPayloadJson()));
        return json.writeValueAsString(value);
    }

    private String systemPrompt() {
        return "你是A股非金融企业财报解读Agent。只能使用evidence中的证据和数字，只能引用现有id；" +
                "输出单个JSON对象，字段为operatingState、confidence、executiveSummary、dimensions、" +
                "positiveSignals、risks、turningPoints、watchpoints、limitations、disclaimer。" +
                "每条实质结论必须有refs；dimensions必须完整覆盖GROWTH、PROFITABILITY、" +
                "EARNINGS_QUALITY、CASH_QUALITY、ASSET_QUALITY、SOLVENCY_CAPITAL_DISCIPLINE。" +
                "不得重新计算、创造数字、给出买卖建议、目标价或收益承诺；事实、推断和观察项分别标记" +
                "FACT、INFERENCE、WATCHPOINT；数据不足时使用INSUFFICIENT_EVIDENCE。只返回JSON。";
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
}
