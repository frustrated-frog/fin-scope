package com.finscope.service.learningcard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.learningcard.StockLearningCardClaim;
import com.finscope.domain.learningcard.StockLearningCardEvidence;
import com.finscope.rpc.llm.LlmChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StockLearningCardSynthesisAgent {
    private final LlmChatClient llm;
    private final ObjectMapper json;

    public StockLearningCardSynthesisAgent(LlmChatClient llm, ObjectMapper json) {
        this.llm = llm; this.json = json;
    }

    public StockLearningCardClaim synthesize(String companyName, String companyCode, String dimension,
                                             List<StockLearningCardEvidence> evidence) throws Exception {
        if (evidence == null || evidence.isEmpty()) return insufficient(dimension, "没有检索到足够的公开资料");
        if (llm == null || !llm.isConfigured()) return insufficient(dimension, "模型暂不可用，已保留公开资料供后续重试");
        String raw = llm.complete(systemPrompt(), json.writeValueAsString(payload(companyName, companyCode,
                dimension, evidence)), 20_000, 900);
        JsonNode root = json.readTree(extractJson(raw));
        validateFields(root);
        StockLearningCardClaim claim = new StockLearningCardClaim();
        claim.setDimensionCode(dimension); claim.setStatus("READY");
        claim.setJudgment(required(root, "judgment", 220));
        claim.setRationale(required(root, "rationale", 420));
        claim.setCounterargument(required(root, "counterargument", 300));
        claim.setUnknowns(required(root, "unknowns", 300));
        claim.setConfidence(required(root, "confidence", 10).toUpperCase());
        if (!Arrays.asList("HIGH", "MEDIUM", "LOW").contains(claim.getConfidence())) {
            throw new IllegalArgumentException("confidence 不合法");
        }
        String combined = claim.getJudgment() + claim.getRationale() + claim.getCounterargument() + claim.getUnknowns();
        if (!StockLearningFramework.isAllowedText(combined)) throw new IllegalArgumentException("输出包含交易语言");
        return claim;
    }

    private StockLearningCardClaim insufficient(String dimension, String reason) {
        StockLearningCardClaim claim = new StockLearningCardClaim();
        claim.setDimensionCode(dimension); claim.setStatus("INSUFFICIENT_EVIDENCE");
        claim.setFailureMessage(reason); claim.setJudgment("证据不足，暂不形成判断");
        claim.setRationale(reason); claim.setCounterargument("仍需继续寻找与当前认识相反的公开材料");
        claim.setUnknowns("当前公开资料未覆盖的变量保持未知"); claim.setConfidence("LOW");
        return claim;
    }

    private Map<String, Object> payload(String name, String code, String dimension,
                                        List<StockLearningCardEvidence> evidence) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("companyName", name); payload.put("companyCode", code); payload.put("dimension", dimension);
        List<Map<String, String>> rows = new ArrayList<Map<String, String>>();
        for (StockLearningCardEvidence item : evidence) {
            Map<String, String> row = new LinkedHashMap<String, String>();
            row.put("id", item.getId()); row.put("title", item.getTitle()); row.put("source", item.getSource());
            row.put("publishedAt", item.getPublishedAt()); row.put("content", compact(item.content(), 1800)); rows.add(row);
        }
        payload.put("evidence", rows); return payload;
    }

    private String systemPrompt() {
        return "你是股票研究学习卡Agent。只根据输入evidence分析指定dimension，不得补充外部事实。"
                + "输出单个JSON对象，只允许judgment、rationale、counterargument、unknowns、confidence。"
                + "判断必须区分事实、推断和未知，rationale应引用证据id，例如[E1]。"
                + "confidence只能是HIGH、MEDIUM、LOW。不得给出买卖、加减仓、目标价、收益承诺或操作建议。只返回JSON。";
    }

    private String required(JsonNode root, String field, int max) {
        if (root == null || !root.has(field) || !root.get(field).isTextual()) throw new IllegalArgumentException(field + " 缺失");
        String value = root.get(field).asText().trim();
        if (value.isEmpty() || value.length() > max) throw new IllegalArgumentException(field + " 长度不合法");
        return value;
    }

    private void validateFields(JsonNode root) {
        if (root == null || !root.isObject()) throw new IllegalArgumentException("输出必须是JSON对象");
        Set<String> allowed = new HashSet<String>(Arrays.asList(
                "judgment", "rationale", "counterargument", "unknowns", "confidence"));
        Set<String> actual = new HashSet<String>();
        Iterator<String> names = root.fieldNames();
        while (names.hasNext()) actual.add(names.next());
        if (!allowed.equals(actual)) throw new IllegalArgumentException("输出字段不符合学习卡契约");
    }

    private String extractJson(String raw) {
        if (raw == null) return "";
        int start = raw.indexOf('{'), end = raw.lastIndexOf('}');
        return start >= 0 && end >= start ? raw.substring(start, end + 1) : raw.trim();
    }

    private String compact(String value, int max) {
        String clean = value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }
}
