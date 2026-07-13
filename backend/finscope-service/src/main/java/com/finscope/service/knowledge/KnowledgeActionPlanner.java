package com.finscope.service.knowledge;

import com.finscope.domain.knowledge.KnowledgeAction;
import com.finscope.domain.knowledge.KnowledgeActionCandidate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class KnowledgeActionPlanner {
    private static final int MAX_ACTIONS = 3;

    public List<KnowledgeAction> plan(List<KnowledgeActionCandidate> candidates) {
        List<KnowledgeActionCandidate> ordered = new ArrayList<KnowledgeActionCandidate>();
        if (candidates != null) {
            ordered.addAll(candidates);
        }
        ordered.sort(Comparator
                .comparingInt((KnowledgeActionCandidate candidate) -> rank(candidate.getType()))
                .thenComparing(Comparator.comparingInt(KnowledgeActionCandidate::getPriority).reversed())
                .thenComparing(KnowledgeActionCandidate::getSortAt,
                        Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder()))
                .thenComparingLong(KnowledgeActionCandidate::getStableId));

        List<KnowledgeAction> actions = new ArrayList<KnowledgeAction>();
        for (KnowledgeActionCandidate candidate : ordered) {
            actions.add(toAction(candidate));
            if (actions.size() == MAX_ACTIONS) {
                break;
            }
        }
        return actions;
    }

    private KnowledgeAction toAction(KnowledgeActionCandidate candidate) {
        KnowledgeAction action = new KnowledgeAction();
        action.setType(candidate.getType());
        action.setTitle(candidate.getTitle());
        action.setTaskId(candidate.getTaskId());
        action.setTopicId(candidate.getTopicId());
        switch (candidate.getType()) {
            case "CONTINUE_TASK":
                action.setReason("这项学习已经开始，继续完成可以沉淀为主题成果。");
                action.setSourceLabel("进行中的学习任务");
                action.setRouteTarget("?section=learning&task=" + candidate.getTaskId());
                return action;
            case "REVIEW_TOPIC":
                action.setReason("该主题已到复习时间，需要重新验证已有判断。");
                action.setSourceLabel("主题复习计划");
                action.setRouteTarget("?section=review&topic=" + candidate.getTopicId());
                return action;
            case "START_TASK":
                action.setReason("这是已接受任务中优先级最高的问题。");
                action.setSourceLabel("已接受的学习任务");
                action.setRouteTarget("?section=learning&task=" + candidate.getTaskId());
                return action;
            case "CHECK_NEW_EVIDENCE":
                action.setReason("上次复习后出现了新证据，值得检查当前结论是否变化。");
                action.setSourceLabel("主题关联事件");
                action.setRouteTarget("?section=topics&topic=" + candidate.getTopicId());
                return action;
            default:
                throw new IllegalArgumentException("Unknown knowledge action type: " + candidate.getType());
        }
    }

    private int rank(String type) {
        if ("CONTINUE_TASK".equals(type)) {
            return 1;
        }
        if ("REVIEW_TOPIC".equals(type)) {
            return 2;
        }
        if ("START_TASK".equals(type)) {
            return 3;
        }
        if ("CHECK_NEW_EVIDENCE".equals(type)) {
            return 4;
        }
        throw new IllegalArgumentException("Unknown knowledge action type: " + type);
    }
}
