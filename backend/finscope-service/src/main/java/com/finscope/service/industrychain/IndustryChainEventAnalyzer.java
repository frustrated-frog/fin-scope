package com.finscope.service.industrychain;

import com.finscope.domain.industrychain.IndustryChainEdge;
import com.finscope.domain.industrychain.IndustryChainEventImpact;
import com.finscope.domain.industrychain.IndustryChainGraph;
import com.finscope.domain.industrychain.IndustryChainNode;
import com.finscope.domain.radar.RadarEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** 用可复现规则把聚合新闻事件映射到已发布的产业链节点。 */
@Component
public class IndustryChainEventAnalyzer {
    private static final String ANALYSIS_VERSION = "RULES_V1";

    public Optional<IndustryChainEventImpact> analyze(IndustryChainGraph graph, RadarEvent event) {
        String content = normalize(text(event.getCanonicalTitle()) + " " + text(event.getSummary()));
        IndustryChainNode direct = bestMatch(graph.getNodes(), content);
        if (direct == null) {
            return Optional.empty();
        }
        IndustryChainEventImpact impact = new IndustryChainEventImpact();
        impact.setChainId(graph.getChainId());
        impact.setRadarEventId(event.getId());
        impact.setDirectNodeKey(direct.getNodeKey());
        impact.setMechanism(mechanism(content));
        impact.setDirection(direction(content));
        impact.setHorizon(horizon(content));
        impact.setConfidence(IndustryChainEventImpact.Confidence.HIGH);
        impact.setAnalysisVersion(ANALYSIS_VERSION);
        impact.setImpactSummary(summary(direct, impact));
        impact.setPathNodeKeys(path(graph, direct.getNodeKey()));
        return Optional.of(impact);
    }

    private IndustryChainNode bestMatch(List<IndustryChainNode> nodes, String content) {
        IndustryChainNode selected = null;
        int bestScore = 0;
        for (IndustryChainNode node : nodes) {
            int score = matchScore(node, content);
            if (score > bestScore || score == bestScore && selected != null && prefer(node, selected)) {
                selected = node;
                bestScore = score;
            }
        }
        return bestScore >= 2 ? selected : null;
    }

    private int matchScore(IndustryChainNode node, String content) {
        String name = normalize(node.getName());
        String code = normalize(node.getStockCode());
        if (!code.isEmpty() && content.contains(code)) {
            return 8;
        }
        if (!name.isEmpty() && content.contains(name)) {
            return Math.max(4, name.length());
        }
        int score = 0;
        for (String token : tokens(name + " " + normalize(node.getDescription()))) {
            if (token.length() >= 2 && content.contains(token)) {
                score = Math.max(score, token.length());
            }
        }
        return score;
    }

    private boolean prefer(IndustryChainNode candidate, IndustryChainNode current) {
        return "PRODUCT".equals(candidate.getType()) && !"PRODUCT".equals(current.getType());
    }

    private List<String> path(IndustryChainGraph graph, String directKey) {
        List<String> result = new ArrayList<String>();
        result.add(directKey);
        Set<String> visited = new HashSet<String>();
        visited.add(directKey);
        String current = directKey;
        while (result.size() < 4) {
            String next = null;
            for (IndustryChainEdge edge : graph.getEdges()) {
                if (current.equals(edge.getSourceKey()) && !visited.contains(edge.getTargetKey())) {
                    next = edge.getTargetKey();
                    break;
                }
            }
            if (next == null) {
                break;
            }
            result.add(next);
            visited.add(next);
            current = next;
        }
        return result;
    }

    private IndustryChainEventImpact.Direction direction(String content) {
        if (containsAny(content, "价格上涨", "报价上涨", "上调报价", "涨价")) {
            return IndustryChainEventImpact.Direction.POSITIVE;
        }
        boolean positive = containsAny(content, "上涨", "涨价", "增长", "扩产", "突破", "中标", "订单", "放量");
        boolean negative = containsAny(content, "下跌", "降价", "下滑", "减产", "制裁", "短缺", "承压", "受阻");
        if (positive && negative) {
            return IndustryChainEventImpact.Direction.MIXED;
        }
        if (positive) {
            return IndustryChainEventImpact.Direction.POSITIVE;
        }
        if (negative) {
            return IndustryChainEventImpact.Direction.NEGATIVE;
        }
        return IndustryChainEventImpact.Direction.UNCERTAIN;
    }

    private IndustryChainEventImpact.Mechanism mechanism(String content) {
        if (containsAny(content, "价格", "报价", "涨价", "降价")) return IndustryChainEventImpact.Mechanism.PRICE;
        if (containsAny(content, "政策", "监管", "补贴", "制裁")) return IndustryChainEventImpact.Mechanism.POLICY;
        if (containsAny(content, "产能", "扩产", "减产")) return IndustryChainEventImpact.Mechanism.CAPACITY;
        if (containsAny(content, "订单", "中标", "合同")) return IndustryChainEventImpact.Mechanism.ORDER;
        if (containsAny(content, "技术", "突破", "发布", "迭代")) return IndustryChainEventImpact.Mechanism.TECHNOLOGY;
        if (containsAny(content, "供应", "供给", "短缺")) return IndustryChainEventImpact.Mechanism.SUPPLY;
        return IndustryChainEventImpact.Mechanism.DEMAND;
    }

    private IndustryChainEventImpact.Horizon horizon(String content) {
        return containsAny(content, "规划", "长期", "未来三年", "五年")
                ? IndustryChainEventImpact.Horizon.LONG : IndustryChainEventImpact.Horizon.SHORT;
    }

    private String summary(IndustryChainNode node, IndustryChainEventImpact impact) {
        return "事件直接作用于“" + node.getName() + "”，可能通过"
                + mechanismLabel(impact.getMechanism()) + "机制沿产业链传导。";
    }

    private String mechanismLabel(String mechanism) {
        switch (mechanism) {
            case "SUPPLY": return "供给";
            case "PRICE": return "价格";
            case "CAPACITY": return "产能";
            case "POLICY": return "政策";
            case "ORDER": return "订单";
            case "TECHNOLOGY": return "技术";
            default: return "需求";
        }
    }

    private List<String> tokens(String value) {
        List<String> result = new ArrayList<String>();
        for (String token : value.split("[^\\p{L}\\p{N}]+")) {
            if (!token.isEmpty()) result.add(token);
        }
        if (value.contains("hbm")) result.add("hbm");
        return result;
    }

    private boolean containsAny(String content, String... values) {
        for (String value : values) {
            if (content.contains(value)) return true;
        }
        return false;
    }

    private String normalize(String value) {
        return text(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private String text(String value) {
        return value == null ? "" : value;
    }
}
