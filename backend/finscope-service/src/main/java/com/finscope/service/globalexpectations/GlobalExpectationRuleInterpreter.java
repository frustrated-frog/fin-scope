package com.finscope.service.globalexpectations;

import com.finscope.domain.globalexpectations.GlobalExpectationEventGroup;
import com.finscope.domain.globalexpectations.GlobalExpectationInterpretation;
import org.springframework.stereotype.Component;

import java.util.List;

/** 先给每个事件生成确定性快读，AI 增强失败时仍可完整展示。 */
@Component
public class GlobalExpectationRuleInterpreter {
    public void interpret(List<GlobalExpectationEventGroup> groups) {
        for (GlobalExpectationEventGroup group : groups) {
            group.setInterpretation(interpret(group));
        }
    }

    private GlobalExpectationInterpretation interpret(GlobalExpectationEventGroup group) {
        String state = text(group.getExpectationRealityState(), "INSUFFICIENT_DATA");
        GlobalExpectationInterpretation result = new GlobalExpectationInterpretation();
        result.setStatus("RULE");
        result.setSource("RULE");
        result.setHappened(happened(group, state));
        result.setMeaning(meaning(state));
        result.setRelatedVariables(relatedVariables(group));
        result.setNextObservation(nextObservation(group, state));
        result.setUncertainty(uncertainty(state));
        return result;
    }

    private String happened(GlobalExpectationEventGroup group, String state) {
        int expectation = value(group.getExpectationScore());
        int reality = value(group.getRealityScore());
        if ("EXPECTATION_LEADING".equals(state)) {
            return "预测市场先行升温：预期活跃度 " + expectation + "，现实侧活跃度 " + reality + "。";
        }
        if ("REALITY_LEADING".equals(state)) {
            return "现实新闻先行升温：近 1 小时出现 " + value(group.getNewsCount1h())
                    + " 条相关信号，预测市场尚未同步。";
        }
        if ("DUAL_ACCELERATING".equals(state)) {
            return "预测市场与现实新闻同时活跃，当前出现双向共振。";
        }
        if ("QUIET".equals(state)) {
            return "预测市场与现实新闻均未达到活跃阈值，事件处于低共振观察期。";
        }
        return "预测市场数据可见，但 Radar 现实侧数据暂不足以形成可靠对照。";
    }

    private String meaning(String state) {
        if ("EXPECTATION_LEADING".equals(state)) {
            return "资金预期可能领先公开信息变化，重点不是追随概率，而是等待现实证据补齐。";
        }
        if ("REALITY_LEADING".equals(state)) {
            return "公开信息变化可能尚未被预测市场充分反映，值得观察概率是否随后调整。";
        }
        if ("DUAL_ACCELERATING".equals(state)) {
            return "市场定价与新闻流同步增强，事件正在从单边信号进入共同关注阶段。";
        }
        if ("QUIET".equals(state)) {
            return "当前没有明显的预期差扩张，信息价值主要来自后续状态切换。";
        }
        return "当前只能解读预测市场一侧，不能据此判断现实世界是否沉寂。";
    }

    private String relatedVariables(GlobalExpectationEventGroup group) {
        if (group.getThemes() == null || group.getThemes().isEmpty()) {
            return "事件结果、预测概率、相关报道频率与独立信源数量。";
        }
        return String.join("、", group.getThemes()) + "；同时观察预测概率、报道频率与信源多样性。";
    }

    private String nextObservation(GlobalExpectationEventGroup group, String state) {
        if ("INSUFFICIENT_DATA".equals(state)) {
            return "等待 Radar 数据恢复，并核对预测概率是否继续跨越当前区间。";
        }
        return "观察下一次概率区间变化、近 1 小时新闻增量，以及独立信源是否继续增加。";
    }

    private String uncertainty(String state) {
        if ("INSUFFICIENT_DATA".equals(state)) {
            return "现实侧查询暂不可用；这不等于没有相关事件或报道。";
        }
        return "预测市场价格代表参与者定价，不等于客观概率；标题匹配也不代表新闻支持某一结果。";
    }

    private int value(Integer source) {
        return source == null ? 0 : source;
    }

    private String text(String source, String fallback) {
        return source == null || source.isBlank() ? fallback : source;
    }
}
