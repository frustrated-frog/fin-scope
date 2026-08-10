package com.finscope.service.learningcard;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockLearningFrameworkTest {
    @Test void exposesTheSixFixedLiujieDimensions() {
        assertEquals(6, StockLearningFramework.dimensions().size());
        assertEquals("SPACE", StockLearningFramework.dimensions().get(0));
        assertEquals("COUNTER_CASE", StockLearningFramework.dimensions().get(5));
        assertFalse(StockLearningFramework.isAllowedText("建议买入并目标价100元"));
        assertFalse(StockLearningFramework.isAllowedText("可以考虑建仓"));
        assertFalse(StockLearningFramework.isAllowedText("建议持有，预计年化收益30%"));
        assertFalse(StockLearningFramework.isAllowedText("上方目标位仍有空间，可以做多"));
    }

    @Test
    void exposesDifferentRequiredSectionsForEachFixedDimension() {
        assertEquals(Arrays.asList("business_map", "growth_drivers", "capture_capacity", "milestones", "constraints"),
                StockLearningFramework.schemaFor("SPACE").requiredKeys());
        assertEquals(Arrays.asList("revenue_engine", "profit_engine", "capital_efficiency", "cash_quality", "earnings_elasticity"),
                StockLearningFramework.schemaFor("PROFIT_MODEL").requiredKeys());
        assertEquals(Arrays.asList("industry_structure", "company_position", "moat", "bargaining_power", "winning_factors"),
                StockLearningFramework.schemaFor("COMPETITION").requiredKeys());
        assertEquals(Arrays.asList("control", "incentive_alignment", "capital_allocation", "controlling_holder_risk", "disclosure_quality", "delivery_record"),
                StockLearningFramework.schemaFor("GOVERNANCE").requiredKeys());
        assertEquals(Arrays.asList("valuation_snapshot", "implied_expectations", "expectation_feasibility", "expectation_gap"),
                StockLearningFramework.schemaFor("VALUATION").requiredKeys());
        assertEquals(Arrays.asList("core_assumptions", "counter_evidence", "falsification_conditions", "stress_scenarios", "leading_risk_indicators", "validation_status"),
                StockLearningFramework.schemaFor("COUNTER_CASE").requiredKeys());
        assertFalse(StockLearningFramework.schemaFor("SPACE").requiredKeys()
                .equals(StockLearningFramework.schemaFor("PROFIT_MODEL").requiredKeys()));
        assertThrows(IllegalArgumentException.class, () -> StockLearningFramework.schemaFor("UNKNOWN"));
    }
}
