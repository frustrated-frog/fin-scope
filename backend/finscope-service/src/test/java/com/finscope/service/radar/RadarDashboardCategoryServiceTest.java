package com.finscope.service.radar;

import com.finscope.domain.radar.RadarEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RadarDashboardCategoryServiceTest {
    private final RadarDashboardCategoryService service = new RadarDashboardCategoryService();

    @Test
    void classifiesTechnologyNewsByItsEventContent() {
        RadarEvent event = event("OpenAI 发布新一代大模型", "新模型提升推理和 Agent 能力", "INDUSTRY");

        assertEquals("TECHNOLOGY", service.classify(event));
    }

    @Test
    void classifiesPolicyActionsBeforeTechnologyTerms() {
        RadarEvent event = event("美国政府收紧 AI 芯片出口限制", "新规扩大半导体出口管制范围", "GLOBAL");

        assertEquals("POLITICS", service.classify(event));
    }

    @Test
    void keepsCompanyAndMarketEventsInFinanceByDefault() {
        RadarEvent event = event("某银行发布年度业绩", "净利润与营业收入同比增长", "COMPANY");

        assertEquals("FINANCE", service.classify(event));
    }

    private RadarEvent event(String title, String summary, String categoryCode) {
        RadarEvent event = new RadarEvent();
        event.setCanonicalTitle(title);
        event.setSummary(summary);
        event.setCategoryCode(categoryCode);
        return event;
    }
}
