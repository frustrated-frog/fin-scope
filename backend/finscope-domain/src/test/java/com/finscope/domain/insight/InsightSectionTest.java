package com.finscope.domain.insight;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InsightSectionTest {

    @Test
    void keepsNoArgsAndValueConstructorsAsPartOfDomainContract() throws Exception {
        InsightSection empty = InsightSection.class.getDeclaredConstructor().newInstance();
        Constructor<InsightSection> values = InsightSection.class.getDeclaredConstructor(String.class, String.class);

        InsightSection section = values.newInstance("市场反应", "风险偏好回升");

        assertNull(empty.getTitle());
        assertEquals("市场反应", section.getTitle());
        assertEquals("风险偏好回升", section.getContent());
    }
}
