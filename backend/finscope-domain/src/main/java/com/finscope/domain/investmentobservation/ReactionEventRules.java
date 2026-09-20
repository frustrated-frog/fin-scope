package com.finscope.domain.investmentobservation;

import com.finscope.common.enums.investmentobservation.ReactionEventSubtype;
import com.finscope.common.enums.investmentobservation.ReactionEventType;
import java.util.regex.Pattern;

/** 规则只接受文本中的主体与动作；不推断受益关系或市场预期。 */
public class ReactionEventRules {
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
        if (text.matches(".*(业绩|季报|年报|半年报|财报|净利润).*")) {
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
        } else if (text.matches(".*(合同|订单|中标).*")) {
            result.setEventType(ReactionEventType.CONTRACT);
            if (text.matches(".*(终止|解除|取消).*")) {
                result.setSubtype(ReactionEventSubtype.CONTRACT_TERMINATED);
            } else if (text.matches(".*(签订|签署).*")) {
                result.setSubtype(ReactionEventSubtype.CONTRACT_SIGNED);
            } else if (text.contains("中标") && !text.matches(".*(候选|拟中标|预中标).*")) {
                result.setSubtype(ReactionEventSubtype.CONTRACT_AWARDED);
            } else {
                result.setSubtype(ReactionEventSubtype.OPERATING_UPDATE);
            }
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
