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
}
