package com.finscope.service.research.agent;

import com.finscope.domain.research.agent.ResearchAgentDecision;
import com.finscope.domain.research.mission.ResearchMission;
import com.finscope.domain.research.mission.ResearchMissionGap;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class DeterministicResearchPolicy {
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
        String subject = subject(context.getMission());
        String[][] templates = templates(subject, intent);
        for (String[] template : templates) {
            ResearchDecisionDraft draft = new ResearchDecisionDraft();
            draft.setDecisionType("TOOL_CALL");
            draft.setCurrentSubgoal(template[0]);
            draft.setToolCode("public_news_search");
            Map<String, Object> arguments = new LinkedHashMap<String, Object>();
            arguments.put("query", template[1]);
            arguments.put("intent", intent);
            draft.setArguments(arguments);
            draft.setTargetGap(targetGap(gap));
            draft.setExpectedObservation("获得能改变证据缺口的独立公开来源");
            draft.setDecisionSummary(template[2]);
            draft.setConfidence(0.66D);
            try {
                return validated(draft, context);
            } catch (IllegalArgumentException repeated) {
                if (repeated.getMessage() == null || !repeated.getMessage().contains("动作指纹已经执行")) {
                    throw repeated;
                }
            }
        }
        return null;
    }

    private ResearchAgentDecision assess(ResearchDecisionContext context, String summary) {
        ResearchDecisionDraft draft = new ResearchDecisionDraft();
        draft.setDecisionType("TOOL_CALL");
        draft.setCurrentSubgoal("刷新证据缺口判断");
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

    private String[][] templates(String subject, String intent) {
        if ("COUNTER".equals(intent)) {
            return new String[][]{
                    {"补齐反方证据", subject + " 风险 下调 延迟 反方证据", "当前材料偏向支持，优先寻找可能证伪命题的事实"},
                    {"寻找反方一手材料", subject + " 指引 下修 订单 资本开支 公司公告", "上一候选动作已尝试，改用公司指引和订单下修信号验证风险"}
            };
        }
        if ("SUPPORT".equals(intent)) {
            return new String[][]{
                    {"补齐支持证据", subject + " 订单 需求 资本开支 最新公告", "支持材料不足，优先验证核心驱动是否真实兑现"},
                    {"寻找支持一手材料", subject + " 财报 指引 合同 公司公告", "改用公司披露验证支持命题的关键事实"}
            };
        }
        if ("PRIMARY".equals(intent)) {
            return new String[][]{
                    {"增加独立一手来源", subject + " 公司公告 财报 监管披露", "当前独立来源不足，优先寻找可核验的一手披露"},
                    {"扩展一手来源覆盖", subject + " 行业数据 官方统计 原始报告", "改用行业和官方数据提升来源独立性"}
            };
        }
        return new String[][]{
                {"寻找最新进展", subject + " 最新进展 公告 数据", "当前证据需要时间维度更新，搜索最新可核验事实"},
                {"补充近期变化", subject + " 近期变化 行业数据 公司披露", "从公司和行业披露补充最新变化"}
        };
    }

    private String targetGap(ResearchMissionGap gap) {
        return "intent=" + resolveIntent(gap) + ",state=" + gap.getStateHash();
    }

    private String subject(ResearchMission mission) {
        return mission == null || mission.getSubject() == null || mission.getSubject().trim().isEmpty()
                ? "研究对象" : mission.getSubject().trim();
    }
}
