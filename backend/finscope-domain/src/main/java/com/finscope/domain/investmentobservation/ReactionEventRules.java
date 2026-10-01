package com.finscope.domain.investmentobservation;

import com.finscope.common.enums.investmentobservation.ReactionEventSubtype;
import com.finscope.common.enums.investmentobservation.ReactionEventType;
import java.util.regex.Pattern;

/** 规则只接受文本中的主体与动作；不推断受益关系或市场预期。 */
public class ReactionEventRules {
    public ReactionEventDecision evaluateMaterial(String title, String body) {
        ReactionEventDecision primary = evaluate(title);
        if (primary.getEventType() != null || primary.getEvidence() != null || body == null) {
            return primary;
        }
        String bounded = body.substring(0, Math.min(body.length(), 12000));
        for (String sentence : bounded.split("[。；;\\n]")) {
            ReactionEventDecision detail = evaluate(sentence.trim());
            if (detail.getEventType() != null && !detail.getSubjects().isEmpty()) {
                return detail;
            }
        }
        return primary;
    }

    public ReactionEventDecision evaluate(String title) {
        ReactionEventDecision result = new ReactionEventDecision();
        String text = title == null ? "" : title.trim();
        if (text.startsWith("【") && text.endsWith("】")) {
            text = text.substring(1, text.length() - 1);
        } else {
            text = text.replaceFirst("^【[^】]*】", "").trim();
        }
        if (text.matches(".*(研报|看好|建议关注|概念股|涨停|股价异动|机构点评|投资评级).*")) {
            result.setEvidence("观点或行情报道，不作为公司自身事件");
            return result;
        }
        // 主动作优先于合同收益说明中的“预计净利润”，否定与拟议不得视为落地。
        boolean contract = text.matches(".*(合同|订单|中标).*");
        boolean explicitEarnings = text.matches(".*(业绩预告|业绩快报|季报|年报|半年报|财报).*");
        if (contract && !explicitEarnings) {
            result.setEventType(ReactionEventType.CONTRACT);
            if (text.matches(".*(未|尚未|没有|并未|不涉及|否认|未曾|不存在).{0,8}(签订|签署|中标|终止|解除|取消|合同|订单).*")) {
                result.setSubtype(ReactionEventSubtype.CONTRACT_DENIED);
            } else if (text.matches(".*(拟|计划|有望|意向|候选|预中标).{0,10}(签订|签署|合同|订单|中标).*")) {
                result.setSubtype(ReactionEventSubtype.CONTRACT_PROPOSED);
            } else if (text.matches(".*(终止|解除|取消).*")) {
                result.setSubtype(ReactionEventSubtype.CONTRACT_TERMINATED);
            } else if (text.matches(".*(签订|签署).*")) {
                result.setSubtype(ReactionEventSubtype.CONTRACT_SIGNED);
            } else if (text.contains("中标") && !text.matches(".*(候选|拟中标|预中标).*")) {
                result.setSubtype(ReactionEventSubtype.CONTRACT_AWARDED);
            } else {
                result.setSubtype(ReactionEventSubtype.OPERATING_UPDATE);
            }
        } else if (text.matches(".*(业绩|季报|年报|半年报|财报|净利润).*")) {
            result.setEventType(ReactionEventType.EARNINGS);
            if (text.matches(".*(修正|上修|下修).*")) {
                result.setSubtype(ReactionEventSubtype.EARNINGS_REVISION);
            } else if (text.contains("预告") || text.contains("预计")) {
                result.setSubtype(ReactionEventSubtype.EARNINGS_FORECAST);
            } else if (text.matches(".*(季报|年报|半年报|财报|报告|披露|发布).*")) {
                result.setSubtype(ReactionEventSubtype.EARNINGS_REPORT);
            } else {
                result.setSubtype(ReactionEventSubtype.OPERATING_UPDATE);
            }
        }
        if (text.matches(".*(签订|签署).*(同时|并|另).*(终止|解除|取消).*")
                || text.matches(".*(尚未|没有|否认).{0,6}(发布|披露|修正).{0,6}(业绩|预告|报告).*")) {
            result.setSubtype(ReactionEventSubtype.UNCLASSIFIED);
        }
        if (result.getEventType() == null) {
            return result;
        }
        var subject = Pattern.compile("^(.{2,45}?)(?:[：:]|发布|披露|签署|签订|中标|终止|解除|取消|上修|下修|修正|预计|获|上半年|前三季度|一季度|净利润|业绩)").matcher(text);
        if (subject.find()) {
            String head = subject.group(1).replaceFirst("^(公告[：:]?|消息[：:]?)", "").trim();
            for (String value : head.split("与|及|、|\\s和\\s")) {
                String name = value.trim();
                if (name.length() >= 2 && name.length() <= 20 && result.getSubjects().size() < 4) {
                    result.getSubjects().add(name);
                }
            }
        }
        result.setEvidence("命中 " + result.getSubtype() + "；主体来自标题动作前或冒号前：" + String.join("、", result.getSubjects()));
        result.setFact(text);
        // 公告编号是强锚点。没有强锚点时只允许同日完整标题一致，不合并相似标题。
        var announcement = Pattern.compile("公告编号[：:]?\\s*([0-9]{4}[-－][0-9]{2,6})").matcher(text);
        if (!result.getSubjects().isEmpty() && announcement.find()) {
            result.setMergeAnchor(String.join("|", result.getSubjects()) + "|" + result.getSubtype() + "|" + announcement.group(1));
        }
        return result;
    }
}
