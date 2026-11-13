package com.finscope.domain.investmentobservation;

import com.finscope.common.enums.investmentobservation.ReactionEventSubtype;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.regex.Pattern;

/** 回购阶段来自直接标题；事项关联仅采用原文明确披露的董事会审议日期。 */
public class ReactionRepurchaseRules {
    private static final Pattern APPROVAL = Pattern.compile(
            "于\\s*(20[0-9]{2})\\s*年\\s*([0-9]{1,2})\\s*月\\s*([0-9]{1,2})\\s*日\\s*召开[^。；]{0,80}?董事(?:会)?[^。；]{0,120}?审议通过[^。；]{0,100}?回购");

    public ReactionEventSubtype classify(String facts) {
        if (!facts.contains("回购") || facts.matches(".*(?:国债|债券|逆回购|可转债|限制性股票|回购注销|质押式回购).*")) {
            return ReactionEventSubtype.UNCLASSIFIED;
        }
        if (!facts.matches("^(?:关于|公司|本公司|拟|计划|提议|董事长提议|取得|获得|获|签署|签订|已|首次|累计|截至|完成|终止|回购|以集中竞价|股份回购|股票回购|不超过|[0-9]).*")) {
            return ReactionEventSubtype.UNCLASSIFIED;
        }
        if (facts.matches(".*(?:尚未|未曾|否认|无计划|暂无计划)[^，。]{0,12}(?:提议|实施|进行|计划|拟)[^，。]{0,8}回购.*")) {
            return ReactionEventSubtype.UNCLASSIFIED;
        }
        if (facts.matches(".*(?:终止|取消)[^，。]{0,20}回购.*|.*回购[^，。]{0,20}(?:终止|取消).*")) {
            if (facts.matches(".*(?:拟|计划|尚未|未)(?:终止|取消).*")) {
                return ReactionEventSubtype.UNCLASSIFIED;
            }
            return ReactionEventSubtype.REPURCHASE_TERMINATED;
        }
        if (facts.matches(".*(?:贷款|融资).*") && facts.matches(".*(?:取得|获得|获|签署|签订|承诺函).*")
                && !facts.matches(".*(?:未取得|尚未取得|拟取得|拟申请|申请中).*")) {
            return ReactionEventSubtype.REPURCHASE_FINANCING;
        }
        if (facts.matches(".*前十(?:名|大).*股东.*持股.*")) {
            return ReactionEventSubtype.REPURCHASE_SHAREHOLDERS;
        }
        if (facts.matches(".*(?:实施完毕|实施结果|回购完成|完成[^，。]{0,16}回购|回购期限届满).*")
                && !facts.matches(".*(?:尚未|未|拟)(?:完成|实施完毕|结束).*")) {
            return ReactionEventSubtype.REPURCHASE_COMPLETED;
        }
        if (facts.matches(".*(?:回购(?:公司)?(?:股份)?(?:的)?(?:实施)?进展|首次回购|累计回购|已回购|回购情况|回购股份情况).*")) {
            return ReactionEventSubtype.REPURCHASE_PROGRESS;
        }
        if (facts.matches(".*(?:预案|方案|提议|拟|审议通过).*") && !facts.matches(".*(?:否认|无计划|暂无计划).*")) {
            return ReactionEventSubtype.REPURCHASE_PLAN;
        }
        return ReactionEventSubtype.UNCLASSIFIED;
    }

    public String planKey(String body) {
        if (body == null) {
            return null;
        }
        var match = APPROVAL.matcher(body.substring(0, Math.min(body.length(), 30000)));
        if (!match.find()) {
            return null;
        }
        try {
            LocalDate date = LocalDate.of(Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)),
                    Integer.parseInt(match.group(3)));
            // 同一原文若涉及多个审批日期，则不自动归并。
            while (match.find()) {
                LocalDate other = LocalDate.of(Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)),
                        Integer.parseInt(match.group(3)));
                if (!date.equals(other)) {
                    return null;
                }
            }
            return "BOARD:" + date;
        } catch (DateTimeException error) {
            return null;
        }
    }
}
