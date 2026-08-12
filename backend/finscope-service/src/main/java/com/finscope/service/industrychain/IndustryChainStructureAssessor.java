package com.finscope.service.industrychain;

import com.finscope.domain.industrychain.IndustryChainEdge;
import com.finscope.domain.industrychain.IndustryChainGraph;
import com.finscope.domain.industrychain.IndustryChainNode;
import com.finscope.domain.industrychain.IndustryChainResearchContent;
import com.finscope.domain.industrychain.IndustryChainStructureAssessment;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 按图结构而非行业名称评估内容完整度。 */
@Service
public class IndustryChainStructureAssessor {
    private static final int TARGET_SEMANTIC_NODES = 9;
    private static final int TARGET_SEMANTIC_TYPES = 4;
    private static final int COMPLETE_SCORE = 80;

    public IndustryChainStructureAssessment assess(IndustryChainGraph graph) {
        IndustryChainStructureAssessment result = new IndustryChainStructureAssessment();
        if (graph == null) {
            result.setStatus("BUILDING");
            result.getGaps().add("等待首版图谱生成");
            return result;
        }

        GraphStructure structure = inspect(graph);
        result.setSemanticNodeCount(structure.semanticNodeKeys.size());
        result.setCoveredStageCount(structure.coveredStageKeys.size());
        result.setStageCount(structure.stageKeys.size());
        result.setScore(score(graph, structure));
        result.setGaps(gaps(graph, structure));
        result.setStatus(status(graph, structure, result.getScore()));
        return result;
    }

    private GraphStructure inspect(IndustryChainGraph graph) {
        GraphStructure result = new GraphStructure();
        for (IndustryChainNode node : safeNodes(graph)) {
            if ("STAGE".equals(node.getType())) {
                result.stageKeys.add(node.getNodeKey());
            } else if (isSemantic(node.getType())) {
                result.semanticNodeKeys.add(node.getNodeKey());
                result.semanticTypes.add(node.getType());
            }
        }
        for (IndustryChainEdge edge : safeEdges(graph)) {
            boolean sourceSemantic = result.semanticNodeKeys.contains(edge.getSourceKey());
            boolean targetSemantic = result.semanticNodeKeys.contains(edge.getTargetKey());
            if (sourceSemantic && result.stageKeys.contains(edge.getTargetKey())) {
                result.coveredStageKeys.add(edge.getTargetKey());
            }
            if (targetSemantic && result.stageKeys.contains(edge.getSourceKey())) {
                result.coveredStageKeys.add(edge.getSourceKey());
            }
            if (sourceSemantic && targetSemantic && !"BELONGS_TO_STAGE".equals(edge.getType())) {
                result.semanticRelationshipCount++;
            }
        }
        IndustryChainResearchContent content = graph.getResearchContent();
        if (content != null && content.getNodeProfiles() != null) {
            for (IndustryChainResearchContent.NodeProfile profile : content.getNodeProfiles()) {
                if (profile != null && result.semanticNodeKeys.contains(profile.getNodeKey())) {
                    result.profiledSemanticKeys.add(profile.getNodeKey());
                }
            }
        }
        return result;
    }

    private int score(IndustryChainGraph graph, GraphStructure structure) {
        int value = "INDUSTRY_CHAIN_V3".equals(graph.getSchemaVersion()) ? 20 : 0;
        value += ratioScore(structure.semanticNodeKeys.size(), TARGET_SEMANTIC_NODES, 20);
        value += ratioScore(structure.coveredStageKeys.size(), structure.stageKeys.size(), 20);
        value += ratioScore(structure.semanticTypes.size(), TARGET_SEMANTIC_TYPES, 15);
        value += ratioScore(structure.profiledSemanticKeys.size(), structure.semanticNodeKeys.size(), 15);
        value += ratioScore(structure.semanticRelationshipCount,
                Math.max(1, structure.semanticNodeKeys.size() / 2), 10);
        return Math.min(100, value);
    }

    private List<String> gaps(IndustryChainGraph graph, GraphStructure structure) {
        List<String> values = new ArrayList<String>();
        if (!"INDUSTRY_CHAIN_V3".equals(graph.getSchemaVersion())) {
            values.add("升级为可展开的 V3 语义图谱");
        }
        if (structure.semanticNodeKeys.size() < TARGET_SEMANTIC_NODES) {
            values.add("补充关键材料、设备、部件、技术或应用语义节点");
        }
        if (structure.coveredStageKeys.size() < structure.stageKeys.size()) {
            values.add("补齐尚未展开的产业环节");
        }
        if (structure.semanticTypes.size() < TARGET_SEMANTIC_TYPES) {
            values.add("增加语义节点类型的多样性");
        }
        if (structure.profiledSemanticKeys.size() < structure.semanticNodeKeys.size()) {
            values.add("补齐节点的价值、瓶颈与国产化画像");
        }
        if (structure.semanticRelationshipCount == 0 && structure.semanticNodeKeys.size() > 1) {
            values.add("补充产品、技术与应用之间的作用关系");
        }
        return values;
    }

    private String status(IndustryChainGraph graph, GraphStructure structure, int score) {
        if (!"INDUSTRY_CHAIN_V3".equals(graph.getSchemaVersion())) {
            return "UPGRADE_AVAILABLE";
        }
        boolean complete = score >= COMPLETE_SCORE
                && structure.semanticNodeKeys.size() >= 6
                && structure.semanticTypes.size() >= TARGET_SEMANTIC_TYPES
                && structure.coveredStageKeys.size() == structure.stageKeys.size();
        return complete ? "COMPLETE" : "ENRICHMENT_RECOMMENDED";
    }

    private int ratioScore(int actual, int target, int points) {
        if (target <= 0) {
            return points;
        }
        return Math.min(points, (int) Math.round(points * Math.min(actual, target) / (double) target));
    }

    private boolean isSemantic(String type) {
        return type != null && !"INDUSTRY_CHAIN".equals(type)
                && !"STAGE".equals(type) && !"COMPANY".equals(type);
    }

    private List<IndustryChainNode> safeNodes(IndustryChainGraph graph) {
        return graph.getNodes() == null ? java.util.Collections.<IndustryChainNode>emptyList() : graph.getNodes();
    }

    private List<IndustryChainEdge> safeEdges(IndustryChainGraph graph) {
        return graph.getEdges() == null ? java.util.Collections.<IndustryChainEdge>emptyList() : graph.getEdges();
    }

    private static final class GraphStructure {
        private final Set<String> stageKeys = new HashSet<String>();
        private final Set<String> semanticNodeKeys = new HashSet<String>();
        private final Set<String> semanticTypes = new HashSet<String>();
        private final Set<String> coveredStageKeys = new HashSet<String>();
        private final Set<String> profiledSemanticKeys = new HashSet<String>();
        private int semanticRelationshipCount;
    }
}
