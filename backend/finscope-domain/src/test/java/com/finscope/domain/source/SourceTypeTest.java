package com.finscope.domain.source;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SourceTypeTest {

    @Test
    void testFromUrl_X() {
        assertEquals(SourceType.X, SourceType.fromUrl("https://x.com/user/status/123"));
        assertEquals(SourceType.X, SourceType.fromUrl("https://twitter.com/user/status/123"));
    }

    @Test
    void testFromUrl_Xinhua() {
        assertEquals(SourceType.XINHUA, SourceType.fromUrl("https://xinhuanet.com/finance/2024-01/article"));
        assertEquals(SourceType.XINHUA, SourceType.fromUrl("https://news.cn/politics/2024-01/article"));
    }

    @Test
    void testFromUrl_OfficialDataSources() {
        assertEquals(SourceType.FEDERAL_RESERVE, SourceType.fromUrl("https://www.federalreserve.gov/newsevents/pressreleases/monetary20260617a.htm"));
        assertEquals(SourceType.BEA, SourceType.fromUrl("https://www.bea.gov/news/2026/gross-domestic-product-second-quarter-2026"));
        assertEquals(SourceType.SEC, SourceType.fromUrl("https://www.sec.gov/newsroom/press-releases/2026-101"));
        assertEquals(SourceType.BIS, SourceType.fromUrl("https://www.bis.org/press/p260620.htm"));
        assertEquals(SourceType.BANK_OF_ENGLAND, SourceType.fromUrl("https://www.bankofengland.co.uk/news/2026/june/monetary-policy-summary"));
        assertEquals(SourceType.IEA, SourceType.fromUrl("https://www.iea.org/news/example-energy-market-report"));
        assertEquals(SourceType.CONFERENCE_BOARD, SourceType.fromUrl("https://www.conference-board.org/topics/consumer-confidence/press/cci-june-2026"));
        assertEquals(SourceType.GOV_CN, SourceType.fromUrl("https://www.gov.cn/zhengce/2026-06/20/content_1234567.htm"));
        assertEquals(SourceType.NBS_CN, SourceType.fromUrl("https://www.stats.gov.cn/sj/zxfb/202606/t20260620_1234567.html"));
        assertEquals(SourceType.CSRC, SourceType.fromUrl("https://www.csrc.gov.cn/csrc/c100028/c7890123/content.shtml"));
    }

    @Test
    void testFromUrl_Eastmoney() {
        assertEquals(SourceType.EASTMONEY, SourceType.fromUrl("https://eastmoney.com/stock/123"));
        assertEquals(SourceType.EASTMONEY, SourceType.fromUrl("https://guba.eastmoney.com/thread/123"));
    }

    @Test
    void testFromUrl_Tonghuashun() {
        assertEquals(SourceType.TONGHUASHUN, SourceType.fromUrl("https://10jqka.com.cn/news/123"));
    }

    @Test
    void testFromUrl_ChineseFinanceSources() {
        assertEquals(SourceType.SECURITIES_TIMES, SourceType.fromUrl("https://www.stcn.com/article/detail/123456.html"));
        assertEquals(SourceType.SECURITIES_STAR, SourceType.fromUrl("https://stock.stockstar.com/IG202606200001.shtml"));
    }

    @Test
    void testFromUrl_Arxiv() {
        assertEquals(SourceType.ARXIV, SourceType.fromUrl("https://arxiv.org/abs/2301.12345"));
    }

    @Test
    void testFromUrl_Unknown() {
        assertEquals(SourceType.WEB, SourceType.fromUrl("https://unknown-site.com/article/123"));
        assertEquals(SourceType.WEB, SourceType.fromUrl(null));
        assertEquals(SourceType.WEB, SourceType.fromUrl(""));
    }

    @Test
    void testFromCode() {
        assertEquals(SourceType.X, SourceType.fromCode("X"));
        assertEquals(SourceType.XINHUA, SourceType.fromCode("XINHUA"));
        assertEquals(SourceType.EASTMONEY, SourceType.fromCode("eastmoney")); // case insensitive
        assertEquals(SourceType.WEB, SourceType.fromCode("UNKNOWN"));
        assertEquals(SourceType.WEB, SourceType.fromCode(null));
    }

    @Test
    void testDisplayName() {
        assertEquals("X (Twitter)", SourceType.X.getDisplayName());
        assertEquals("新华网", SourceType.XINHUA.getDisplayName());
        assertEquals("东方财富", SourceType.EASTMONEY.getDisplayName());
        assertEquals("同花顺", SourceType.TONGHUASHUN.getDisplayName());
        assertEquals("美联储", SourceType.FEDERAL_RESERVE.getDisplayName());
    }

    @Test
    void testCredibility() {
        assertEquals(5, SourceType.XINHUA.getCredibility()); // 官方媒体
        assertEquals(5, SourceType.SEC.getCredibility()); // 官方机构
        assertEquals(4, SourceType.EASTMONEY.getCredibility()); // 财经媒体
        assertEquals(3, SourceType.X.getCredibility()); // 社交媒体
    }

    @Test
    void testCategory() {
        assertEquals("新闻", SourceType.XINHUA.getCategory());
        assertEquals("财经", SourceType.EASTMONEY.getCategory());
        assertEquals("社交", SourceType.X.getCategory());
        assertEquals("研究", SourceType.ARXIV.getCategory());
    }
}
