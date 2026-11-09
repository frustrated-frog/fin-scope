package com.finscope.domain.investmentobservation;

import com.finscope.common.enums.investmentobservation.ReactionEventSubtype;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReactionEventRulesTest {
    private final ReactionEventRules rules = new ReactionEventRules();

    @Test
    void keepsFullBracketHeadlinesAndCompanyNamesContainingHe() {
        assertEquals(java.util.List.of("和而泰"), rules.evaluate("【和而泰签订重大合同】").getSubjects());
        assertEquals(java.util.List.of("示例公司"), rules.evaluate("示例公司终止此前签订的合同").getSubjects());
    }

    @Test
    void separatesEventStageAndRejectsResearchOpinion() {
        assertNull(rules.evaluate("中信证券：看好胜宏科技业绩增长").getEventType());
        assertEquals(ReactionEventSubtype.CONTRACT_TERMINATED, rules.evaluate("示例公司终止此前签订的合同").getSubtype());
        assertEquals(ReactionEventSubtype.OPERATING_UPDATE, rules.evaluate("胜宏科技：订单饱满").getSubtype());
        assertEquals(ReactionEventSubtype.EARNINGS_REVISION, rules.evaluate("示例公司上修年度业绩预告").getSubtype());
        assertEquals(ReactionEventSubtype.CONTRACT_SIGNED, rules.evaluate("示例公司签订重大合同").getSubtype());
        assertNull(rules.evaluate("示例公司：订单饱满").getMergeAnchor());
    }
    @Test
    void negationPlansAndContractProfitDoNotBecomeCompletedOrEarningsEvents() {
        assertEquals(ReactionEventSubtype.CONTRACT_DENIED, rules.evaluate("示例公司：目前未签订重大合同").getSubtype());
        assertEquals(ReactionEventSubtype.CONTRACT_PROPOSED, rules.evaluate("示例公司：拟签订重大合同").getSubtype());
        assertEquals(ReactionEventSubtype.CONTRACT_SIGNED, rules.evaluate("示例公司：签订重大合同，预计新增净利润1亿元").getSubtype());
        assertEquals(ReactionEventSubtype.CONTRACT_SIGNED,
                rules.evaluateMaterial("最新公告", "示例公司签订重大合同。客户为其他公司。").getSubtype());
    }

    @Test
    void requiresOwnCompanyActionAndSeparatesConcreteDisclosureStages() {
        var signed = rules.evaluate("2连板东方新能：签署9.12亿元算力板卡采购合同");
        assertEquals(java.util.List.of("东方新能"), signed.getSubjects());
        assertEquals(ReactionEventSubtype.CONTRACT_SIGNED, signed.getSubtype());
        assertEquals(ReactionEventSubtype.EARNINGS_REPORT, rules.evaluate("行云科技：前三季度净利润为2.69亿元 同比扭亏为盈").getSubtype());
        assertEquals(ReactionEventSubtype.EARNINGS_FORECAST, rules.evaluate("行云科技：预计前三季度净利润2.69亿元").getSubtype());
        assertEquals(ReactionEventSubtype.EARNINGS_FORECAST, rules.evaluate("行云科技：Q3净利润预计环比增长569%-716%").getSubtype());
        assertEquals(ReactionEventSubtype.CONTRACT_AMENDED, rules.evaluate("行云科技：全资子公司上调算力服务合同至33.57亿元 较原合同提升约9.96%").getSubtype());
        assertEquals(ReactionEventSubtype.CONTRACT_PROPOSED, rules.evaluate("行云科技：成为项目中标候选人").getSubtype());
        assertFalse(rules.evaluate("中信证券：行云科技中标某项目").getSubtype().isSpecific());
        assertFalse(rules.evaluate("某公司：订单饱满、需求持续增长").getSubtype().isSpecific());
    }

    @Test
    void truncatedAmendmentOnlyJoinsWhenTheBodyCorroboratesTheHeadlineAmount() {
        var time = java.time.LocalDateTime.parse("2026-10-12T20:00:00");
        var original = ReactionEventSubtype.OPERATING_UPDATE;
        var full = ReactionHistoricalCase.from("行云科技：上调算力服务合同至33.57亿元 较原合同提升约9.96%",
                null, "行云科技", "300209.SZ", time, original, true, "REACTION_RULES_V2");
        String truncated = "行云科技：深圳行云新合同金额升至33.57亿元 较原合同金...";
        var same = ReactionHistoricalCase.from(truncated, "深圳行云此前签署算力服务协议，协议总金额上涨至33.57亿元，较原合同金额提升约9.96%。",
                "行云科技", "300209.SZ", time, original, true, "REACTION_RULES_V2");
        var conflict = ReactionHistoricalCase.from(truncated, "深圳行云此前签署算力服务协议，协议总金额上涨至36亿元，较原合同金额提升约9.96%。",
                "行云科技", "300209.SZ", time, original, true, "REACTION_RULES_V2");
        assertNotNull(full.key());
        assertEquals(full, same);
        assertNull(conflict.key());
    }

}
