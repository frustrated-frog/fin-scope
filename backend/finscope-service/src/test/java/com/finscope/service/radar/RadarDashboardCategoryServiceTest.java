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

    @Test
    void keepsMarketMovesInFinanceEvenWhenTheyMentionAiOrTechnologyCompanies() {
        RadarEvent event = event("AI应用端反复走强 博彦科技2连板",
                "AI应用端反复走强，博彦科技涨停，相关个股跟涨。", "MARKET_MOVE");

        assertEquals("FINANCE", service.classify(event));
    }

    @Test
    void keepsCompanyOperatingDisclosuresOutOfTheStrictTechnologyBoard() {
        RadarEvent event = event("壹连科技：对BE的订单与出货量环比持续增长",
                "公司订单交付节奏有序，相关业务占整体营业收入比例仍较小。", "COMPANY");

        assertEquals("FINANCE", service.classify(event));
    }

    @Test
    void keepsAiRevenueCommentaryOutOfTheStrictTechnologyBoard() {
        RadarEvent event = event("微软AI收入高度依赖OpenAI",
                "市场关注微软人工智能收入来源与合作依赖。", "COMPANY");

        assertEquals("FINANCE", service.classify(event));
    }

    @Test
    void keepsIpoAndEquityCommentaryOutOfTheStrictTechnologyBoard() {
        RadarEvent event = event("光量子芯片领先企业图灵量子启动IPO辅导",
                "机构称量子计算先发者通吃，这两家公司间接持有图灵量子股权。", "UNCLASSIFIED");

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
