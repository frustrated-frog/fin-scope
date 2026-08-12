package com.finscope.service.industrychain;

import com.finscope.domain.industrychain.IndustryChainEdge;
import com.finscope.domain.industrychain.IndustryChainEvidence;
import com.finscope.domain.industrychain.IndustryChainGraph;
import com.finscope.domain.industrychain.IndustryChainNode;
import com.finscope.domain.industrychain.IndustryChainResearchContent;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** 在图谱发布前集中校验结构、证据和关系语义。 */
@Service
public class IndustryChainGraphValidator {
    private static final Set<String> NODE_TYPES = set("INDUSTRY_CHAIN", "STAGE", "MATERIAL", "EQUIPMENT",
            "COMPONENT", "PRODUCT", "TECHNOLOGY", "APPLICATION", "COMPANY");
    private static final Set<String> EDGE_TYPES = set("CONTAINS_STAGE", "FLOWS_TO", "BELONGS_TO_STAGE",
            "INPUT_TO", "PRODUCES", "PARTICIPATES_IN", "SUPPLIES_TO", "DEPENDS_ON", "ENABLES",
            "USED_IN", "SUBSTITUTES", "COMPETES_WITH");
    private static final Set<String> NATURES = set("DISCLOSED", "INDUSTRY_LOGIC", "INFERRED");
    private static final Set<String> CONFIDENCE = set("HIGH", "MEDIUM", "LOW");
    private static final Set<String> LIFECYCLES = set("EMERGING", "GROWTH", "MATURE", "CONSOLIDATING", "DECLINING");
    private static final Set<String> PROSPERITY = set("RISING", "STABLE", "COOLING", "MIXED");
    private static final Set<String> SUPPLY_DEMAND = set("TIGHT", "BALANCED", "LOOSE", "STRUCTURAL");
    private static final Set<String> MATURITY = set("EMERGING", "SCALING", "MATURE", "DECLINING");
    private static final Set<String> LEVELS = set("HIGH", "MEDIUM", "LOW");
    private static final Set<String> LOCALIZATION = set("LOW", "MEDIUM", "HIGH", "LEADING");
    private static final Set<String> EDGE_STRENGTH = set("PRIMARY", "SECONDARY");

    public IndustryChainGraph validate(IndustryChainGraph graph) {
        if (graph == null || blank(graph.getName()) || graph.getNodes() == null || graph.getEdges() == null) {
            throw new IllegalArgumentException("产业链图谱缺少基础字段");
        }
        Set<String> evidenceCodes = evidenceCodes(graph.getEvidence());
        Map<String, IndustryChainNode> nodes = nodes(graph.getNodes(), evidenceCodes);
        validateEdges(graph.getEdges(), nodes, evidenceCodes);
        validateStageFlow(graph.getNodes(), graph.getEdges());
        validateV3SemanticDepth(graph, nodes);
        validateResearchContent(graph.getResearchContent(), nodes,
                "INDUSTRY_CHAIN_V3".equals(graph.getSchemaVersion()));
        return graph;
    }

    private void validateV3SemanticDepth(IndustryChainGraph graph, Map<String, IndustryChainNode> nodes) {
        if (!"INDUSTRY_CHAIN_V3".equals(graph.getSchemaVersion())
                || nodes.values().stream().noneMatch(node -> "INDUSTRY_CHAIN".equals(node.getType()))) {
            return;
        }
        Map<String, Set<String>> childrenByStage = new HashMap<String, Set<String>>();
        for (IndustryChainNode node : nodes.values()) {
            if ("STAGE".equals(node.getType())) {
                childrenByStage.put(node.getNodeKey(), new HashSet<String>());
            }
        }
        for (IndustryChainEdge edge : graph.getEdges()) {
            IndustryChainNode source = nodes.get(edge.getSourceKey());
            if ("BELONGS_TO_STAGE".equals(edge.getType()) && source != null
                    && isSemanticNode(source) && childrenByStage.containsKey(edge.getTargetKey())) {
                childrenByStage.get(edge.getTargetKey()).add(source.getNodeKey());
            }
        }
        Set<String> shallowStages = new TreeSet<String>();
        Set<String> semanticNodes = new HashSet<String>();
        for (Map.Entry<String, Set<String>> item : childrenByStage.entrySet()) {
            semanticNodes.addAll(item.getValue());
            if (item.getValue().isEmpty()) {
                shallowStages.add(item.getKey());
            }
        }
        if (!shallowStages.isEmpty()) {
            throw new IllegalArgumentException("V3 产业环节缺少直接语义子节点：" + String.join(",", shallowStages));
        }
        if (semanticNodes.size() < 9) {
            throw new IllegalArgumentException("V3 图谱语义节点不足 9 个：" + semanticNodes.size());
        }
    }

    private boolean isSemanticNode(IndustryChainNode node) {
        return !"INDUSTRY_CHAIN".equals(node.getType())
                && !"STAGE".equals(node.getType())
                && !"COMPANY".equals(node.getType());
    }

    private void validateResearchContent(IndustryChainResearchContent content,
                                         Map<String, IndustryChainNode> nodes,
                                         boolean requireCompleteCoverage) {
        if (content == null) {
            if (requireCompleteCoverage) {
                throw new IllegalArgumentException("V3 图谱缺少研究画像");
            }
            return;
        }
        IndustryChainResearchContent.Overview overview = content.getOverview();
        if (overview != null) {
            validateOptionalEnum(overview.getLifecycle(), LIFECYCLES, "产业生命周期");
            validateOptionalEnum(overview.getProsperity(), PROSPERITY, "产业景气度");
            validateOptionalEnum(overview.getSupplyDemand(), SUPPLY_DEMAND, "产业供需状态");
        }
        Set<String> stageKeys = new HashSet<String>();
        for (IndustryChainResearchContent.StageProfile profile : safeStages(content)) {
            validateProfileNode(profile.getNodeKey(), "STAGE", nodes, stageKeys, "环节画像");
            validateRequiredEnum(profile.getLifecycle(), LIFECYCLES, "环节生命周期");
            validateRequiredEnum(profile.getProsperity(), PROSPERITY, "环节景气度");
            validateRequiredEnum(profile.getSupplyDemand(), SUPPLY_DEMAND, "环节供需状态");
        }
        Set<String> companyKeys = new HashSet<String>();
        for (IndustryChainResearchContent.CompanyProfile profile : safeCompanies(content)) {
            validateProfileNode(profile.getNodeKey(), "COMPANY", nodes, companyKeys, "公司画像");
        }
        Set<String> nodeKeys = new HashSet<String>();
        for (IndustryChainResearchContent.NodeProfile profile : safeNodeProfiles(content)) {
            validateProfileNode(profile.getNodeKey(), null, nodes, nodeKeys, "节点画像");
            validateRequiredEnum(profile.getMaturity(), MATURITY, "节点成熟度");
            validateRequiredEnum(profile.getValueLevel(), LEVELS, "节点价值等级");
            validateRequiredEnum(profile.getBottleneckLevel(), LEVELS, "节点瓶颈等级");
            validateRequiredEnum(profile.getLocalizationLevel(), LOCALIZATION, "节点国产化等级");
        }
        if (requireCompleteCoverage) {
            validateCoverage(nodes, stageKeys, companyKeys);
        }
    }

    private void validateCoverage(Map<String, IndustryChainNode> nodes,
                                  Set<String> stageProfiles,
                                  Set<String> companyProfiles) {
        Set<String> missingStages = new TreeSet<String>();
        Set<String> missingCompanies = new TreeSet<String>();
        for (IndustryChainNode node : nodes.values()) {
            if ("STAGE".equals(node.getType()) && !stageProfiles.contains(node.getNodeKey())) {
                missingStages.add(node.getNodeKey());
            } else if ("COMPANY".equals(node.getType()) && !companyProfiles.contains(node.getNodeKey())) {
                missingCompanies.add(node.getNodeKey());
            }
        }
        if (!missingStages.isEmpty()) {
            throw new IllegalArgumentException("V3 环节画像未覆盖全部产业环节：" + String.join(",", missingStages));
        }
        if (!missingCompanies.isEmpty()) {
            throw new IllegalArgumentException("V3 公司画像未覆盖全部公司节点：" + String.join(",", missingCompanies));
        }
    }

    private List<IndustryChainResearchContent.StageProfile> safeStages(IndustryChainResearchContent content) {
        return content.getStageProfiles() == null
                ? Collections.<IndustryChainResearchContent.StageProfile>emptyList() : content.getStageProfiles();
    }

    private List<IndustryChainResearchContent.CompanyProfile> safeCompanies(IndustryChainResearchContent content) {
        return content.getCompanyProfiles() == null
                ? Collections.<IndustryChainResearchContent.CompanyProfile>emptyList() : content.getCompanyProfiles();
    }

    private List<IndustryChainResearchContent.NodeProfile> safeNodeProfiles(IndustryChainResearchContent content) {
        return content.getNodeProfiles() == null
                ? Collections.<IndustryChainResearchContent.NodeProfile>emptyList() : content.getNodeProfiles();
    }

    private void validateProfileNode(String nodeKey, String expectedType,
                                     Map<String, IndustryChainNode> nodes, Set<String> seen, String label) {
        IndustryChainNode node = nodes.get(nodeKey);
        if (blank(nodeKey) || node == null || (expectedType != null && !expectedType.equals(node.getType()))
                || !seen.add(nodeKey)) {
            throw new IllegalArgumentException(label + "引用的节点无效或重复：" + nodeKey);
        }
    }

    private void validateOptionalEnum(String value, Set<String> allowed, String label) {
        if (!blank(value)) {
            validateRequiredEnum(value, allowed, label);
        }
    }

    private void validateRequiredEnum(String value, Set<String> allowed, String label) {
        if (blank(value) || !allowed.contains(value)) {
            throw new IllegalArgumentException(label + "无效：" + value);
        }
    }

    private Set<String> evidenceCodes(List<IndustryChainEvidence> evidence) {
        Set<String> codes = new HashSet<String>();
        if (evidence != null) {
            for (IndustryChainEvidence item : evidence) {
                if (item == null || blank(item.getEvidenceCode()) || !codes.add(item.getEvidenceCode())) {
                    throw new IllegalArgumentException("产业链证据编号无效或重复");
                }
            }
        }
        if (codes.isEmpty()) {
            throw new IllegalArgumentException("产业链图谱缺少公开证据");
        }
        return codes;
    }

    private Map<String, IndustryChainNode> nodes(List<IndustryChainNode> values, Set<String> evidenceCodes) {
        Map<String, IndustryChainNode> result = new HashMap<String, IndustryChainNode>();
        for (IndustryChainNode node : values) {
            if (node == null) {
                throw new IllegalArgumentException("产业链节点不能为空");
            }
            if (blank(node.getNodeKey())) {
                throw new IllegalArgumentException("产业链节点缺少 nodeKey");
            }
            if (result.containsKey(node.getNodeKey())) {
                throw new IllegalArgumentException("产业链节点 nodeKey 重复：" + node.getNodeKey());
            }
            if (!NODE_TYPES.contains(node.getType())) {
                throw new IllegalArgumentException("产业链节点类型无效：nodeKey=" + node.getNodeKey()
                        + ", type=" + node.getType());
            }
            if (blank(node.getName())) {
                throw new IllegalArgumentException("产业链节点名称为空：nodeKey=" + node.getNodeKey());
            }
            if (!CONFIDENCE.contains(node.getConfidence())) {
                throw new IllegalArgumentException("产业链节点置信度无效：nodeKey=" + node.getNodeKey()
                        + ", confidence=" + node.getConfidence());
            }
            validateEvidenceRefs(node.getEvidenceRefs(), evidenceCodes);
            result.put(node.getNodeKey(), node);
        }
        return result;
    }

    private void validateEdges(List<IndustryChainEdge> values, Map<String, IndustryChainNode> nodes,
                               Set<String> evidenceCodes) {
        Set<String> keys = new HashSet<String>();
        for (IndustryChainEdge edge : values) {
            if (edge == null || blank(edge.getEdgeKey()) || !keys.add(edge.getEdgeKey())
                    || !EDGE_TYPES.contains(edge.getType()) || !NATURES.contains(edge.getNature())
                    || !CONFIDENCE.contains(edge.getConfidence())) {
                throw new IllegalArgumentException("产业链关系字段无效或重复");
            }
            if (!nodes.containsKey(edge.getSourceKey()) || !nodes.containsKey(edge.getTargetKey())
                    || edge.getSourceKey().equals(edge.getTargetKey())) {
                throw new IllegalArgumentException("产业链关系端点无效");
            }
            if ("SUPPLIES_TO".equals(edge.getType()) && !"DISCLOSED".equals(edge.getNature())) {
                throw new IllegalArgumentException("企业供销关系必须来自明确披露");
            }
            validateOptionalEnum(edge.getStrength(), EDGE_STRENGTH, "产业链关系强度");
            validateEvidenceRefs(edge.getEvidenceRefs(), evidenceCodes);
        }
    }

    private void validateStageFlow(List<IndustryChainNode> nodes, List<IndustryChainEdge> edges) {
        Map<String, Integer> stageOrders = new HashMap<String, Integer>();
        for (IndustryChainNode node : nodes) {
            if ("STAGE".equals(node.getType())) {
                if (node.getStageOrder() == null || node.getStageOrder() <= 0) {
                    throw new IllegalArgumentException("产业环节缺少有效顺序");
                }
                stageOrders.put(node.getNodeKey(), node.getStageOrder());
            }
        }
        if (stageOrders.size() < 3) {
            throw new IllegalArgumentException("产业链至少需要三个环节");
        }
        Map<String, Set<String>> outgoing = new HashMap<String, Set<String>>();
        Map<String, Integer> indegree = new HashMap<String, Integer>();
        for (String key : stageOrders.keySet()) {
            outgoing.put(key, new HashSet<String>());
            indegree.put(key, 0);
        }
        for (IndustryChainEdge edge : edges) {
            if ("FLOWS_TO".equals(edge.getType()) && stageOrders.containsKey(edge.getSourceKey())
                    && stageOrders.containsKey(edge.getTargetKey())
                    && outgoing.get(edge.getSourceKey()).add(edge.getTargetKey())) {
                indegree.put(edge.getTargetKey(), indegree.get(edge.getTargetKey()) + 1);
            }
        }
        ArrayDeque<String> queue = new ArrayDeque<String>();
        for (Map.Entry<String, Integer> item : indegree.entrySet()) {
            if (item.getValue() == 0) {
                queue.add(item.getKey());
            }
        }
        int visited = 0;
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            visited++;
            for (String target : outgoing.get(current)) {
                int next = indegree.get(target) - 1;
                indegree.put(target, next);
                if (next == 0) {
                    queue.add(target);
                }
            }
        }
        if (visited != stageOrders.size()) {
            throw new IllegalArgumentException("产业环节主干不能包含循环");
        }
        String first = Collections.min(stageOrders.entrySet(), Map.Entry.comparingByValue()).getKey();
        String last = Collections.max(stageOrders.entrySet(), Map.Entry.comparingByValue()).getKey();
        if (!reachable(first, last, outgoing)) {
            throw new IllegalArgumentException("产业链缺少从上游到终端的完整路径");
        }
    }

    private boolean reachable(String source, String target, Map<String, Set<String>> outgoing) {
        ArrayDeque<String> queue = new ArrayDeque<String>();
        Set<String> visited = new HashSet<String>();
        queue.add(source);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (target.equals(current)) {
                return true;
            }
            if (visited.add(current)) {
                queue.addAll(outgoing.getOrDefault(current, Collections.<String>emptySet()));
            }
        }
        return false;
    }

    private void validateEvidenceRefs(List<String> refs, Set<String> available) {
        if (refs == null || refs.isEmpty()) {
            throw new IllegalArgumentException("产业链节点或关系缺少证据引用");
        }
        for (String ref : refs) {
            if (!available.contains(ref)) {
                throw new IllegalArgumentException("产业链节点或关系引用了未知证据");
            }
        }
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static Set<String> set(String... values) {
        return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(values)));
    }
}
