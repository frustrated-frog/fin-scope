package com.finscope.domain.investmentobservation;

import com.finscope.common.enums.investmentobservation.ReactionEventSubtype;
import com.finscope.common.enums.investmentobservation.ReactionEventType;

import java.util.regex.Pattern;

/** 规则只接受文本中的主体与动作；不推断受益关系或市场预期。 */
public class ReactionEventRules {
    private static final String PERIOD = "(?:前三季度|[一二三四1234]季度|第[一二三四1234]季|[一二三四]季报|半年度|上半年|下半年|半年报|年度|年报|[0-9]{2,4}年|[1一][至-][369]月|[Qq][1-4])";
    private static final Pattern PREFIX = Pattern.compile("^([\\p{IsHan}\\p{Alnum}*·.&（）()、及与\\s-]{2,45})[：:]");
    private static final Pattern ACTION = Pattern.compile("^(.{2,45}?)(?=拟签|拟中标|预中标|签署|签订|签下|中标|终止|解除|取消|上修|下修|修正|发布|披露|预计|预告|获得|调增|调减|上调|下调|"
            + PERIOD + "|净利润|归母净利|业绩|拟|回购|提议|取得|完成)");
    private static final Pattern ANNOUNCEMENT = Pattern.compile("公告编号[：:]?\\s*([0-9]{4})[-－]([0-9]{2,6})");
    private static final Pattern DIRECT_FACT = Pattern.compile("^(?:[，,\\s]*(?:公司|本公司|全资子公司|控股子公司|全资孙公司|控股孙公司|子公司|孙公司|联合体|目前|近日|今日|已|正式))*[，,\\s]*"
            + "(?:签|中标|预中标|终止|解除|取消|变更|调整|调增|调减|上调|下调|拟|计划|尚未|未|没有|否认|并未|获得|获中标|发布|披露|预计|修正|上修|下修|"
            + "与[^，,]{1,28}(?:签|拟签)|成为[^，,]{0,24}中标候选|净利|归母净利|营收|营业收入|业绩|今年|去年|前三季度|" + PERIOD
            + "|[^，,]{0,14}(?:合同|订单)[^，,]{0,16}(?:金额|总额|调增|调减|上调|下调|变更|调整|提高|增至|升至|终止|解除|取消|已签))");

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
        String text = title == null ? "" : title.strip();
        if (text.startsWith("【") && text.endsWith("】")) {
            text = text.substring(1, text.length() - 1);
        }
        text = text.replaceFirst("^【[^】]*】\\s*", "")
                .replaceFirst("^(?:\\d+连板|\\d+天\\d+板|\\*?ST(?=[A-Za-z]))[：:\\s]*", "").strip();
        if (text.matches(".*(研报|看好|建议关注|概念股|股价异动|机构点评|投资评级|券商晨会|公告精选|公告一览|公司要闻|晚间公告汇总|收盘综述|传闻|据传|网传).*")) {
            result.setEvidence("观点、传闻或汇总不作为公司自身事件");
            return result;
        }

        var prefix = PREFIX.matcher(text);
        var action = ACTION.matcher(text);
        boolean hasPrefix = prefix.find() && !ACTION.matcher(prefix.group(1)).find();
        String head = hasPrefix ? prefix.group(1).trim() : action.find() ? action.group(1).trim() : "";
        String facts = hasPrefix ? text.substring(prefix.end()).strip() : text.substring(head.length()).strip();
        if (facts.matches("^(?:认为|建议|指出|解读|点评|展望).*|^[^，。；]{0,22}(?:有望|或将|值得关注).*")
                && !facts.matches(".*(?:拟签|预中标|业绩预告).*")) {
            return result;
        }

        ReactionEventSubtype subtype = new ReactionRepurchaseRules().classify(facts);
        if (subtype.isSpecific()) {
            result.setEventType(ReactionEventType.REPURCHASE);
        } else {
            subtype = contractStage(facts);
        }
        if (result.getEventType() == null && subtype.isSpecific()) {
            result.setEventType(ReactionEventType.CONTRACT);
        } else if (result.getEventType() == null && facts.matches(".*(业绩|季报|年报|半年报|财报|净利|营业收入|营收).*")) {
            result.setEventType(ReactionEventType.EARNINGS);
            subtype = earningsStage(facts);
        } else if (subtype == ReactionEventSubtype.OPERATING_UPDATE) {
            result.setEventType(ReactionEventType.CONTRACT);
        }
        if (result.getEventType() == null) {
            return result;
        }
        result.setSubtype(result.getEventType() != ReactionEventType.REPURCHASE
                && hasPrefix && subtype.isSpecific() && !DIRECT_FACT.matcher(facts).find()
                ? ReactionEventSubtype.OPERATING_UPDATE : subtype);
        for (String value : head.split("与|及|、|\\s和\\s")) {
            String name = value.trim();
            if (name.matches("[\\p{IsHan}\\p{Alnum}*·.&（）() -]{2,32}") && result.getSubjects().size() < 4) {
                result.getSubjects().add(name);
            }
        }
        result.setEvidence("命中 " + subtype + "；标题主体：" + String.join("、", result.getSubjects()));
        result.setFact(text);
        var announcement = ANNOUNCEMENT.matcher(text);
        if (!result.getSubjects().isEmpty() && subtype.isSpecific() && announcement.find()) {
            result.setMergeAnchor(String.join("|", result.getSubjects()) + "|" + subtype + "|" + announcement.group(1) + "-" + announcement.group(2));
        }
        return result;
    }

    private ReactionEventSubtype contractStage(String facts) {
        String action = facts.split("[。；;]", 2)[0];
        if (!action.matches(".*(合同|订单|中标|协议).*")) {
            return ReactionEventSubtype.UNCLASSIFIED;
        }
        if (action.matches(".*(?:业绩预告|业绩快报|季报|年报|半年报).*(?:合同|订单|中标|协议).*")) {
            return ReactionEventSubtype.UNCLASSIFIED;
        }
        if (action.matches(".*(签订|签署).*(同时|并|另).*(终止|解除|取消).*")) {
            return ReactionEventSubtype.OPERATING_UPDATE;
        }
        if (action.matches(".*(尚未|没有|并未|否认|未曾|未)(?:与[^，,]{1,20})?\\s*(签订|签署|中标|终止|解除|取消).*")) {
            return ReactionEventSubtype.CONTRACT_DENIED;
        }
        if (action.matches(".*(中标候选|预中标|拟中标|(?:拟|计划)[^，,]{0,20}(?:签署|签订)).*")) {
            return ReactionEventSubtype.CONTRACT_PROPOSED;
        }
        if (action.matches(".*(?:终止|解除|取消)[^，,]{0,28}(?:合同|订单|协议).*")
                || action.matches(".*(?:合同|订单|协议)[^，,]{0,12}(?:终止|解除|取消).*")) {
            return ReactionEventSubtype.CONTRACT_TERMINATED;
        }
        if (action.matches(".*(?:合同|订单|协议)[^，,]{0,24}(?:调增|调减|上调|下调|调整|变更|提高|降至|增至|升至).*")
                || action.matches(".*(?:调增|调减|上调|下调|调整|变更)[^，,]{0,14}(?:合同|订单|协议).*")) {
            return ReactionEventSubtype.CONTRACT_AMENDED;
        }
        if (action.matches(".*(?:签订|签署|签下|签订了|签署了)[^，,]{0,50}(?:合同|订单|协议).*")
                || action.matches(".*(?:合同|订单)[^，,]{0,14}(?:已签订|已签署).*")) {
            return ReactionEventSubtype.CONTRACT_SIGNED;
        }
        if (action.matches(".*中标.*") && !action.matches(".*(?:招标|中标情况|中标结果待定|能否中标|尚未确定).*")) {
            return ReactionEventSubtype.CONTRACT_AWARDED;
        }
        return ReactionEventSubtype.OPERATING_UPDATE;
    }

    private ReactionEventSubtype earningsStage(String facts) {
        if (facts.matches(".*(?:尚未|没有|否认).{0,8}(?:发布|披露|修正).{0,8}(?:业绩|预告|报告).*")) {
            return ReactionEventSubtype.UNCLASSIFIED;
        }
        if (facts.matches(".*(?:修正|上修|下修).*(?:业绩预告|净利).*")) {
            return ReactionEventSubtype.EARNINGS_REVISION;
        }
        if (facts.contains("业绩预告") || facts.matches(".*(?:预计.*(?:净利|营收|营业收入)|(?:净利[润]?|营收|营业收入)[^，。；]{0,12}预计).*")
                && facts.matches(".*" + PERIOD + ".*")) {
            return ReactionEventSubtype.EARNINGS_FORECAST;
        }
        if (facts.matches(".*(?:业绩快报|[一二三四]季报|年报|半年报|财报|年度报告|季度报告).*")
                || facts.matches(".*" + PERIOD + ".*(?:净利|营收|营业收入).*[0-9].*")) {
            return ReactionEventSubtype.EARNINGS_REPORT;
        }
        return ReactionEventSubtype.OPERATING_UPDATE;
    }
}
