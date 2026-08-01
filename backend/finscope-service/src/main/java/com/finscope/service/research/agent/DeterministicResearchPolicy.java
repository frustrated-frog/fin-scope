package com.finscope.service.research.agent;

import com.finscope.domain.research.agent.ResearchAgentDecision;
import com.finscope.domain.research.mission.ResearchMissionGap;
import com.finscope.domain.research.mission.ResearchMissionTask;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DeterministicResearchPolicy {
    private static final Pattern A_SHARE_CODE = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)");
    private final ResearchDecisionValidator validator;

    public DeterministicResearchPolicy(ResearchDecisionValidator validator) {
        this.validator = validator;
    }

    public ResearchAgentDecision decide(ResearchDecisionContext context) {
        ResearchMissionGap gap = context.getLatestGap();
        if (gap != null && gap.isSufficient()) {
            return validated(terminal("FINISH", "提交研究完成校验",
                    "当前证据已达到门槛，请由独立完成校验器确认是否进入报告生成", 0.9D), context);
        }
        if (context.getRemainingActions() <= 0) {
            return validated(terminal("ABORT", "停止外部研究动作",
                    "外部动作预算已经用尽，保留当前证据和轨迹并安全终止", 1D), context);
        }
        if (gap == null) {
            return assess(context, "尚无证据缺口快照，先评估当前研究状态");
        }

        String intent = resolveIntent(gap);
        if (intent != null) {
            ResearchAgentDecision search = firstUntriedSearch(context, intent, gap);
            if (search != null) {
                return search;
            }
        }
        try {
            return assess(context, "候选搜索动作已经尝试，重新评估证据状态");
        } catch (IllegalArgumentException repeatedAssessment) {
            return validated(terminal("ABORT", "停止重复研究动作",
                    "没有可安全执行的新动作，避免重复搜索和循环空转", 1D), context);
        }
    }

    private ResearchAgentDecision firstUntriedSearch(ResearchDecisionContext context,
                                                     String intent,
                                                     ResearchMissionGap gap) {
        if (context.getTasks() == null || context.getTasks().isEmpty()) {
            return legacySearch(context, intent, gap);
        }
        for (ResearchMissionTask task : orderedReadyTasks(context, intent)) {
            try {
                return validated(toolDecision(task, gap), context);
            } catch (IllegalArgumentException repeated) {
                if (repeated.getMessage() == null || !repeated.getMessage().contains("动作指纹已经执行")) {
                    throw repeated;
                }
            }
        }
        return null;
    }

    private ResearchAgentDecision legacySearch(ResearchDecisionContext context,
                                                String intent,
                                                ResearchMissionGap gap) {
        String subject = context.getMission() == null || context.getMission().getSubject() == null
                ? "研究对象" : context.getMission().getSubject();
        Matcher matcher = A_SHARE_CODE.matcher(subject);
        if ("PRIMARY".equals(intent) && matcher.find()) {
            String stockCode = matcher.group(1);
            String[][] materials = {{"ANNOUNCEMENT", "财报 业绩 经营"}, {"INTERACTION", "经营 订单 客户"}};
            for (String[] material : materials) {
                ResearchDecisionDraft draft = new ResearchDecisionDraft();
                draft.setDecisionType("TOOL_CALL");
                draft.setCurrentSubgoal("补充" + material[0] + "资料");
                draft.setToolCode("research_material_search");
                Map<String, Object> arguments = new LinkedHashMap<String, Object>();
                arguments.put("stockCode", stockCode);
                arguments.put("materialType", material[0]);
                arguments.put("query", material[1]);
                draft.setArguments(arguments);
                draft.setTargetGap(targetGap(gap));
                draft.setExpectedObservation("获得可追溯的结构化研究材料");
                draft.setDecisionSummary("优先读取与缺口匹配的一手或专业资料");
                draft.setConfidence(0.78D);
                try {
                    return validated(draft, context);
                } catch (IllegalArgumentException repeated) {
                    if (repeated.getMessage() == null || !repeated.getMessage().contains("动作指纹已经执行")) throw repeated;
                }
            }
        }
        String[][] queries = "COUNTER".equals(intent)
                ? new String[][]{{subject + " 风险 下调 延迟 反方证据", "寻找反方证据"},
                {subject + " 指引 下修 订单 资本开支 公司公告", "寻找反方一手材料"}}
                : new String[][]{{subject + " 订单 需求 资本开支 最新公告", "补齐支持证据"},
                {subject + " 公司公告 财报 监管披露", "增加独立一手来源"}};
        for (String[] query : queries) {
            ResearchDecisionDraft draft = new ResearchDecisionDraft();
            draft.setDecisionType("TOOL_CALL");
            draft.setCurrentSubgoal(query[1]);
            draft.setToolCode("public_news_search");
            Map<String, Object> arguments = new LinkedHashMap<String, Object>();
            arguments.put("query", query[0]);
            arguments.put("intent", intent);
            draft.setArguments(arguments);
            draft.setTargetGap(targetGap(gap));
            draft.setExpectedObservation("获得能改变证据缺口的独立公开来源");
            draft.setDecisionSummary("按最新证据缺口执行受控检索");
            draft.setConfidence(0.66D);
            try {
                return validated(draft, context);
            } catch (IllegalArgumentException repeated) {
                if (repeated.getMessage() == null || !repeated.getMessage().contains("动作指纹已经执行")) throw repeated;
            }
        }
        return null;
    }

    private ResearchDecisionDraft toolDecision(ResearchMissionTask task, ResearchMissionGap gap) {
        ResearchDecisionDraft draft = new ResearchDecisionDraft();
        draft.setDecisionType("TOOL_CALL");
        draft.setCurrentSubgoal(task.getTitle());
        draft.setMissionTaskKey(task.getTaskKey());
        draft.setToolCode(task.getToolCode());
        Map<String, Object> arguments = new LinkedHashMap<String, Object>();
        if ("public_news_search".equals(task.getToolCode())) {
            arguments.put("query", task.getQueryText());
            arguments.put("intent", task.getIntent());
        } else if ("research_material_search".equals(task.getToolCode())) {
            String[] parts = task.getQueryText().trim().split("\\s+", 3);
            arguments.put("stockCode", parts[0]);
            arguments.put("materialType", parts[1]);
            arguments.put("query", parts.length == 3 ? parts[2] : "");
        }
        draft.setArguments(arguments);
        draft.setTargetGap(targetGap(gap));
        draft.setExpectedObservation(task.getExpectedEvidence());
        draft.setDecisionSummary(task.getRationale());
        draft.setConfidence("research_material_search".equals(task.getToolCode()) ? 0.78D : 0.66D);
        return draft;
    }

    private ResearchAgentDecision assess(ResearchDecisionContext context, String summary) {
        ResearchDecisionDraft draft = new ResearchDecisionDraft();
        draft.setDecisionType("TOOL_CALL");
        draft.setCurrentSubgoal("刷新证据缺口判断");
        ResearchMissionTask task = firstReadyTask(context, "ASSESS", "evidence_assess");
        if (task != null) draft.setMissionTaskKey(task.getTaskKey());
        draft.setToolCode("evidence_assess");
        draft.setArguments(Collections.<String, Object>emptyMap());
        draft.setTargetGap(context.getLatestGap() == null ? "NO_GAP" : context.getLatestGap().getStateHash());
        draft.setExpectedObservation("获得最新证据数量、独立来源和正反分布");
        draft.setDecisionSummary(summary);
        draft.setConfidence(0.72D);
        return validated(draft, context);
    }

    private ResearchDecisionDraft terminal(String type, String subgoal, String summary, double confidence) {
        ResearchDecisionDraft draft = new ResearchDecisionDraft();
        draft.setDecisionType(type);
        draft.setCurrentSubgoal(subgoal);
        draft.setDecisionSummary(summary);
        draft.setConfidence(confidence);
        return draft;
    }

    private ResearchAgentDecision validated(ResearchDecisionDraft draft, ResearchDecisionContext context) {
        return validator.validate(draft, context, "DETERMINISTIC");
    }

    private String resolveIntent(ResearchMissionGap gap) {
        String recommended = gap.getRecommendedIntent() == null
                ? "" : gap.getRecommendedIntent().trim().toUpperCase(Locale.ROOT);
        if ("COUNTER".equals(recommended) || gap.getCounterCount() == 0) return "COUNTER";
        if ("SUPPORT".equals(recommended) || gap.getSupportCount() == 0) return "SUPPORT";
        if ("PRIMARY".equals(recommended) || gap.getSourceCount() < 2) return "PRIMARY";
        if ("UPDATE".equals(recommended)) return "UPDATE";
        return null;
    }

    private java.util.List<ResearchMissionTask> orderedReadyTasks(ResearchDecisionContext context, String intent) {
        java.util.List<ResearchMissionTask> values = new java.util.ArrayList<ResearchMissionTask>();
        for (ResearchMissionTask task : context.getTasks()) {
            if (ready(context, task) && intent.equals(task.getIntent()) && isSearch(task)) values.add(task);
        }
        if (!values.isEmpty()) return values;
        for (ResearchMissionTask task : context.getTasks()) {
            if (ready(context, task) && isSearch(task)) values.add(task);
        }
        return values;
    }

    private ResearchMissionTask firstReadyTask(ResearchDecisionContext context, String intent, String tool) {
        for (ResearchMissionTask task : context.getTasks()) {
            if (ready(context, task) && intent.equals(task.getIntent()) && tool.equals(task.getToolCode())) return task;
        }
        return null;
    }

    private boolean ready(ResearchDecisionContext context, ResearchMissionTask task) {
        if (!("PENDING".equals(task.getStatus()) || "FAILED".equals(task.getStatus())
                || "INTERRUPTED".equals(task.getStatus()))) return false;
        for (String dependency : task.getDependencies()) {
            ResearchMissionTask value = null;
            for (ResearchMissionTask candidate : context.getTasks()) {
                if (dependency.equals(candidate.getTaskKey())) value = candidate;
            }
            if (value == null || !("COMPLETED".equals(value.getStatus())
                    || ("SKIPPED".equals(value.getStatus())
                    && "SUFFICIENT_EVIDENCE".equals(value.getSkipReason())))) return false;
        }
        return true;
    }

    private boolean isSearch(ResearchMissionTask task) {
        return "public_news_search".equals(task.getToolCode())
                || "research_material_search".equals(task.getToolCode());
    }

    private String targetGap(ResearchMissionGap gap) {
        return "intent=" + resolveIntent(gap) + ",state=" + gap.getStateHash();
    }

}
