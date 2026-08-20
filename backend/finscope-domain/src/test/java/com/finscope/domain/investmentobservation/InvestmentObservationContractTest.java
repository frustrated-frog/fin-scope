package com.finscope.domain.investmentobservation;

import com.finscope.common.enums.investmentobservation.InvestmentObservationDisposition;
import com.finscope.common.enums.investmentobservation.InvestmentObservationSourceType;
import com.finscope.common.enums.investmentobservation.InvestmentObservationStage;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InvestmentObservationContractTest {

    @Test
    void exposesStableLifecycleValues() {
        assertEquals("FOCUS", InvestmentObservationStage.FOCUS.name());
        assertEquals("TRACKING", InvestmentObservationStage.TRACKING.name());
        assertEquals("LEARNING", InvestmentObservationStage.LEARNING.name());
        assertEquals("ARCHIVED", InvestmentObservationStage.ARCHIVED.name());
        assertEquals("ACTIVE", InvestmentObservationDisposition.ACTIVE.name());
        assertEquals("RADAR_EVENT", InvestmentObservationSourceType.RADAR_EVENT.name());
    }

    @Test
    void carriesWorkspaceCardsScoreDimensionsAndTransitions() {
        InvestmentObservationScoreDimension dimension = new InvestmentObservationScoreDimension();
        dimension.setCode("CHANGE");
        dimension.setLabel("变化强度");
        dimension.setScore(18);
        dimension.setMaxScore(20);
        dimension.setExplanation("出现可验证的经营变化");

        InvestmentObservation observation = new InvestmentObservation();
        observation.setId(7L);
        observation.setSourceType(InvestmentObservationSourceType.RADAR_EVENT);
        observation.setSourceId(11L);
        observation.setTitle("算力资本开支持续上修");
        observation.setStage(InvestmentObservationStage.FOCUS);
        observation.setScore(82);
        observation.setScoreDimensions(Collections.singletonList(dimension));
        observation.setDisposition(InvestmentObservationDisposition.ACTIVE);

        InvestmentObservationTransition transition = new InvestmentObservationTransition();
        transition.setObservationId(7L);
        transition.setFromStage(InvestmentObservationStage.TRACKING);
        transition.setToStage(InvestmentObservationStage.FOCUS);
        transition.setReason("新增独立来源确认");

        InvestmentObservationWorkspace workspace = new InvestmentObservationWorkspace();
        workspace.setFocus(Collections.singletonList(observation));
        workspace.setTracking(Collections.<InvestmentObservation>emptyList());
        workspace.setLearning(Collections.<InvestmentObservation>emptyList());
        workspace.setTransitions(Collections.singletonList(transition));
        workspace.setActiveCount(1);
        workspace.setChangedTodayCount(1);

        assertEquals(1, workspace.getFocus().size());
        assertEquals(82, workspace.getFocus().get(0).getScore());
        assertEquals("CHANGE", workspace.getFocus().get(0).getScoreDimensions().get(0).getCode());
        assertEquals("新增独立来源确认", workspace.getTransitions().get(0).getReason());
        assertEquals(1, workspace.getActiveCount());
        assertEquals(1, workspace.getChangedTodayCount());
    }
}
