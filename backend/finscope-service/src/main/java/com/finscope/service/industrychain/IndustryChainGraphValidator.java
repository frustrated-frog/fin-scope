package com.finscope.service.industrychain;

import com.finscope.domain.industrychain.IndustryChainEdge;
import com.finscope.domain.industrychain.IndustryChainEvidence;
import com.finscope.domain.industrychain.IndustryChainGraph;
import com.finscope.domain.industrychain.IndustryChainNode;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 在图谱发布前集中校验结构、证据和关系语义。 */
@Service
public class IndustryChainGraphValidator {
    private static final Set<String> NODE_TYPES = set("INDUSTRY_CHAIN", "STAGE", "PRODUCT", "COMPANY");
    private static final Set<String> EDGE_TYPES = set("CONTAINS_STAGE", "FLOWS_TO", "BELONGS_TO_STAGE",
            "INPUT_TO", "PRODUCES", "PARTICIPATES_IN", "SUPPLIES_TO");
    private static final Set<String> NATURES = set("DISCLOSED", "INDUSTRY_LOGIC", "INFERRED");
    private static final Set<String> CONFIDENCE = set("HIGH", "MEDIUM", "LOW");

    public IndustryChainGraph validate(IndustryChainGraph graph) {
        if (graph == null || blank(graph.getName()) || graph.getNodes() == null || graph.getEdges() == null) {
            throw new IllegalArgumentException("产业链图谱缺少基础字段");
        }
        Set<String> evidenceCodes = evidenceCodes(graph.getEvidence());
        Map<String, IndustryChainNode> nodes = nodes(graph.getNodes(), evidenceCodes);
        validateEdges(graph.getEdges(), nodes, evidenceCodes);
        validateStageFlow(graph.getNodes(), graph.getEdges());
        return graph;
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
