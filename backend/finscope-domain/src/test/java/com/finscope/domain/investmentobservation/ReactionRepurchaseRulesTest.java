package com.finscope.domain.investmentobservation;

import com.finscope.common.enums.investmentobservation.ReactionEventSubtype;
import com.finscope.common.enums.investmentobservation.ReactionEventType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ReactionRepurchaseRulesTest {
    private final ReactionEventRules rules = new ReactionEventRules();

    @Test
    void recognizesHangdianPlanAndBothDisclosures() {
        assertStage("杭电股份：拟5000万元—1亿元回购公司股份", ReactionEventSubtype.REPURCHASE_PLAN);
        assertStage("杭电股份:关于以集中竞价交易方式回购股份的预案", ReactionEventSubtype.REPURCHASE_PLAN);
        assertStage("杭电股份:关于取得股票回购专项贷款承诺函的公告", ReactionEventSubtype.REPURCHASE_FINANCING);
        assertStage("杭电股份:关于回购股份事项前十名股东和前十名无限售条件股东持股情况的公告",
                ReactionEventSubtype.REPURCHASE_SHAREHOLDERS);
        assertStage("杭电股份：关于首次回购公司股份的公告", ReactionEventSubtype.REPURCHASE_PROGRESS);
        assertStage("杭电股份：关于以集中竞价交易方式回购股份的进展公告", ReactionEventSubtype.REPURCHASE_PROGRESS);
        assertStage("杭电股份：关于股份回购实施结果的公告", ReactionEventSubtype.REPURCHASE_COMPLETED);
        assertStage("杭电股份：关于终止回购股份方案的公告", ReactionEventSubtype.REPURCHASE_TERMINATED);
    }

    @Test
    void excludesBondTradesRestrictedStockAndOtherCompanyActions() {
        for (String title : java.util.List.of("杭电股份：拟回购注销限制性股票", "示例公司：拟开展国债逆回购",
                "杭电股份：尚未取得股票回购专项贷款承诺函", "杭电股份：暂无计划回购股份",
                "杭电股份：关于拟终止回购股份方案的公告", "杭电股份：回购股份尚未实施完毕",
                "券商公司：杭电股份拟回购公司股份", "平安银行：为杭电股份提供股票回购专项贷款")) {
            assertNotEquals(ReactionEventType.REPURCHASE, rules.evaluate(title).getEventType(), title);
        }
    }

    @Test
    void linksOnlyExplicitSameBoardApprovalAndPreservesStageAndTime() {
        String title = "杭电股份:关于取得股票回购专项贷款承诺函的公告";
        String loan = "公司于2026年11月11日召开第六届董事第六次会议，审议通过了《关于以集中竞价交易方式回购股份的预案的议案》。";
        String shareholders = "公司于 2026 年 11 月 11 日召开第六届董事会第六次会议，审议通过了《关于以集中竞价交易方式回购股份的预案的议案》。";
        var time = LocalDateTime.parse("2026-11-12T17:33:44");
        var financing = ReactionHistoricalCase.from(title, loan, "杭电股份", "603618.SH", time,
                ReactionEventSubtype.REPURCHASE_FINANCING, true, "REACTION_RULES_V3");
        var holding = ReactionHistoricalCase.from("杭电股份:关于回购股份事项前十名股东持股情况的公告", shareholders,
                "杭电股份", "603618.SH", time.plusDays(1), ReactionEventSubtype.REPURCHASE_SHAREHOLDERS, true, "REACTION_RULES_V3");
        assertNotNull(financing.topicKey());
        assertEquals(financing.topicKey(), holding.topicKey());
        assertNotEquals(financing.subtype(), holding.subtype());
        assertNull(new ReactionRepurchaseRules().planKey("公司计划回购股份，当前年度第二次回购。"));
        assertEquals("BOARD:2026-11-11", new ReactionRepurchaseRules().planKey(
                "具体内容详见公司于2026年11月6日在上交所披露的《董事长提议公司回购股份的提示性公告》（公告编号：2026-064）\n"
                + shareholders));
        assertNotEquals(financing.topicKey(), ReactionHistoricalCase.from(title, loan.replace("11月11日", "12月11日"),
                "杭电股份", "603618.SH", time.plusMonths(1), ReactionEventSubtype.REPURCHASE_FINANCING, true,
                "REACTION_RULES_V3").topicKey());
    }

    private void assertStage(String title, ReactionEventSubtype subtype) {
        var decision = rules.evaluate(title);
        assertEquals(ReactionEventType.REPURCHASE, decision.getEventType(), title);
        assertEquals(subtype, decision.getSubtype(), title);
        assertEquals(java.util.List.of("杭电股份"), decision.getSubjects(), title);
    }
}
