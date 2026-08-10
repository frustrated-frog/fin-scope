package com.finscope.service.supplychain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.supplychain.StockSupplyChainEvidence;
import com.finscope.domain.supplychain.StockSupplyChainNode;
import com.finscope.domain.supplychain.StockSupplyChainSnapshot;
import com.finscope.rpc.llm.LlmChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 将冻结的公开证据包归纳为严格、可追溯的三层产业链。 */
@Service
public class StockSupplyChainSynthesisAgent {
    private static final Set<String> ROOT_FIELDS = set("summary", "position", "limitations", "nodes");
    private static final Set<String> NODE_FIELDS = set(
            "layer", "name", "relationType", "description", "confidence", "evidenceRefs");
    private static final Set<String> LAYERS = set("UPSTREAM", "COMPANY", "DOWNSTREAM");
    private static final Set<String> CONFIDENCE = set("HIGH", "MEDIUM", "LOW");
    private static final List<String> FORBIDDEN = Arrays.asList(
            "建议买入", "建议卖出", "买入", "卖出", "目标价", "建仓", "加仓", "减仓");

    private final LlmChatClient llm;
    private final ObjectMapper objectMapper;

    public StockSupplyChainSynthesisAgent(LlmChatClient llm, ObjectMapper objectMapper) {
        this.llm = llm;
        this.objectMapper = objectMapper;
    }

    public StockSupplyChainSnapshot synthesize(String companyName, String companyCode,
                                                List<StockSupplyChainEvidence> evidence) throws Exception {
        if (evidence == null || evidence.isEmpty()) {
            throw new IllegalArgumentException("产业链归纳缺少公开证据");
        }
        if (llm == null || !llm.isConfigured()) {
            throw new IllegalStateException("产业链归纳模型暂不可用");
        }
        String raw = llm.complete(systemPrompt(), objectMapper.writeValueAsString(
                payload(companyName, companyCode, evidence)), 20_000, 1800);
        JsonNode root = objectMapper.readTree(extractJson(raw));
        validateFields(root, ROOT_FIELDS);
        StockSupplyChainSnapshot snapshot = new StockSupplyChainSnapshot();
        snapshot.setCompanyName(companyName);
        snapshot.setCompanyCode(companyCode);
        snapshot.setSummary(required(root, "summary", 360));
        snapshot.setPosition(required(root, "position", 120));
        snapshot.setLimitations(required(root, "limitations", 500));
        snapshot.setNodes(nodes(root.get("nodes"), evidence));
        snapshot.setEvidence(evidence);
        snapshot.setEvidenceAsOf(evidenceAsOf(evidence));
        snapshot.setSchemaVersion("SUPPLY_CHAIN_V1");
        snapshot.setModel(llm.modelName());
        snapshot.setGeneratedAt(LocalDateTime.now());
        rejectTradingLanguage(snapshot);
        return snapshot;
    }

    private List<StockSupplyChainNode> nodes(JsonNode values,
                                             List<StockSupplyChainEvidence> evidence) {
        if (values == null || !values.isArray() || values.size() == 0 || values.size() > 18) {
            throw new IllegalArgumentException("nodes 必须包含 1 至 18 个节点");
        }
        Set<String> availableEvidence = new HashSet<String>();
        for (StockSupplyChainEvidence item : evidence) {
            availableEvidence.add(item.getEvidenceCode());
        }
        Set<String> seenLayers = new HashSet<String>();
        List<StockSupplyChainNode> result = new ArrayList<StockSupplyChainNode>();
        for (JsonNode value : values) {
            validateFields(value, NODE_FIELDS);
            StockSupplyChainNode node = new StockSupplyChainNode();
            node.setLayer(enumValue(value, "layer", LAYERS));
            node.setName(required(value, "name", 80));
            node.setRelationType(required(value, "relationType", 60));
            node.setDescription(required(value, "description", 360));
            node.setConfidence(enumValue(value, "confidence", CONFIDENCE));
            node.setEvidenceRefs(evidenceRefs(value.get("evidenceRefs"), availableEvidence));
            seenLayers.add(node.getLayer());
            result.add(node);
        }
        if (!seenLayers.containsAll(LAYERS)) {
            throw new IllegalArgumentException("产业链必须同时包含上游、公司和下游节点");
        }
        return result;
    }

    private List<String> evidenceRefs(JsonNode values, Set<String> available) {
        if (values == null || !values.isArray() || values.size() == 0) {
            throw new IllegalArgumentException("每个产业链节点必须引用公开证据");
        }
        Set<String> result = new LinkedHashSet<String>();
        for (JsonNode value : values) {
            if (!value.isTextual() || !available.contains(value.asText())) {
                throw new IllegalArgumentException("产业链节点引用了未知证据");
            }
            result.add(value.asText());
        }
        return new ArrayList<String>(result);
    }

    private Map<String, Object> payload(String companyName, String companyCode,
                                        List<StockSupplyChainEvidence> evidence) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("companyName", companyName);
        value.put("companyCode", companyCode);
        value.put("evidence", evidence);
        return value;
    }

    private String systemPrompt() {
        return "你是谨慎的上市公司产业链研究员。只依据输入 evidence 输出一个 JSON 对象，不要 markdown。"
                + "根字段只能是 summary、position、limitations、nodes。nodes 每项只能包含 layer、name、"
                + "relationType、description、confidence、evidenceRefs。layer 只能为 UPSTREAM、COMPANY、"
                + "DOWNSTREAM，三层都必须出现；confidence 只能为 HIGH、MEDIUM、LOW。每个节点至少引用一个"
                + "输入中存在的 evidenceCode。匿名客户或供应商不得猜测具体公司，行业推断必须使用 LOW。"
                + "禁止投资建议、目标价和虚构客户、供应商、合同。";
    }

    private LocalDate evidenceAsOf(List<StockSupplyChainEvidence> evidence) {
        LocalDate latest = null;
        for (StockSupplyChainEvidence item : evidence) {
            String value = item.getPublishedAt();
            if (value == null || value.length() < 10) {
                continue;
            }
            try {
                LocalDate date = LocalDate.parse(value.substring(0, 10));
                if (latest == null || date.isAfter(latest)) {
                    latest = date;
                }
            } catch (RuntimeException ignored) {
                // 搜索供应商的非标准日期不参与 evidenceAsOf 计算。
            }
        }
        return latest;
    }

    private void rejectTradingLanguage(StockSupplyChainSnapshot snapshot) {
        StringBuilder text = new StringBuilder(snapshot.getSummary())
                .append(snapshot.getPosition()).append(snapshot.getLimitations());
        for (StockSupplyChainNode node : snapshot.getNodes()) {
            text.append(node.getName()).append(node.getDescription());
        }
        for (String token : FORBIDDEN) {
            if (text.indexOf(token) >= 0) {
                throw new IllegalArgumentException("产业链结果包含交易指令");
            }
        }
    }

    private void validateFields(JsonNode node, Set<String> allowed) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("产业链模型输出必须是 JSON 对象");
        }
        Iterator<String> fields = node.fieldNames();
        Set<String> actual = new HashSet<String>();
        while (fields.hasNext()) {
            actual.add(fields.next());
        }
        if (!actual.equals(allowed)) {
            throw new IllegalArgumentException("产业链模型输出字段不符合契约");
        }
    }

    private String required(JsonNode node, String field, int maxLength) {
        JsonNode value = node.get(field);
        String text = value == null || !value.isTextual() ? "" : value.asText().trim();
        if (text.isEmpty() || text.length() > maxLength) {
            throw new IllegalArgumentException(field + " 缺失或超过长度限制");
        }
        return text;
    }

    private String enumValue(JsonNode node, String field, Set<String> allowed) {
        String value = required(node, field, 40);
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException(field + " 不在允许范围内");
        }
        return value;
    }

    private String extractJson(String raw) {
        String value = raw == null ? "" : raw.trim();
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("产业链模型未返回 JSON 对象");
        }
        return value.substring(start, end + 1);
    }

    private static Set<String> set(String... values) {
        return java.util.Collections.unmodifiableSet(
                new LinkedHashSet<String>(Arrays.asList(values)));
    }
}
