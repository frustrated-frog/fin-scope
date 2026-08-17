package com.finscope.service.globalexpectations;

import com.finscope.domain.globalexpectations.GlobalExpectationEventGroup;
import com.finscope.domain.globalexpectations.GlobalExpectationInterpretation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExpectationRuleInterpreterTest {
    @Test
    void givesEveryCardAnImmediateFivePartQuickRead() {
        GlobalExpectationEventGroup group = new GlobalExpectationEventGroup();
        group.setTitle("美联储利率预期");
        group.setThemes(List.of("经济", "财务"));
        group.setExpectationRealityState("EXPECTATION_LEADING");
        group.setExpectationScore(72);
        group.setRealityScore(15);
        group.setGapReasons(List.of("预测市场先动，现实侧新闻较少"));

        new GlobalExpectationRuleInterpreter().interpret(List.of(group));

        GlobalExpectationInterpretation result = group.getInterpretation();
        assertEquals("RULE", result.getStatus());
        assertEquals("RULE", result.getSource());
        assertFalse(result.getHappened().isBlank());
        assertFalse(result.getMeaning().isBlank());
        assertFalse(result.getRelatedVariables().isBlank());
        assertFalse(result.getNextObservation().isBlank());
        assertFalse(result.getUncertainty().isBlank());
    }

    @Test
    void describesInsufficientRealityDataAsABoundaryNotAsNoNews() {
        GlobalExpectationEventGroup group = new GlobalExpectationEventGroup();
        group.setTitle("地缘冲突预期");
        group.setThemes(List.of("地缘冲突"));
        group.setExpectationRealityState("INSUFFICIENT_DATA");
        group.setExpectationScore(68);
        group.setRealityScore(0);

        new GlobalExpectationRuleInterpreter().interpret(List.of(group));

        assertFalse(group.getInterpretation().getUncertainty().contains("没有新闻"));
        assertFalse(group.getInterpretation().getUncertainty().isBlank());
    }
}
