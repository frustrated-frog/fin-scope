package com.finscope.rpc.source;

import com.finscope.domain.fetch.RawItem;
import com.finscope.domain.source.Source;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebArticleExtractorTest {

    private final WebArticleExtractor extractor = new WebArticleExtractor();

    @Test
    void extractsFederalReserveArticleFromOfficialPage() {
        String url = "https://www.federalreserve.gov/newsevents/pressreleases/monetary20260617a.htm";
        RawItem item = extractor.extract(document(url, federalReserveHtml()), source(url));

        assertEquals("Federal Reserve issues FOMC statement", item.getTitle());
        assertTrue(item.getBody().contains("The Federal Reserve issued the Federal Open Market Committee statement"));
        assertTrue(item.getBody().contains("The Committee seeks to achieve maximum employment and inflation at the rate of 2 percent"));
        assertFalse(item.getBody().contains("Share this page"));
        assertEquals(LocalDateTime.of(2026, 6, 17, 0, 0), item.getPublishedAt());
        assertEquals("WEB_PAGE", item.getContentType());
        assertEquals("web:profile:federal-reserve", item.getExtractionMethod());
        assertTrue(item.getQualityScore() >= 80);
    }

    @Test
    void extractsSecReleaseFromStructuredContent() {
        String url = "https://www.sec.gov/newsroom/press-releases/2026-101";
        RawItem item = extractor.extract(document(url, secHtml()), source(url));

        assertEquals("SEC Charges Company With Misleading Revenue Disclosures", item.getTitle());
        assertTrue(item.getSummary().contains("The Securities and Exchange Commission today charged"));
        assertTrue(item.getBody().contains("The Securities and Exchange Commission today charged Example Corp."));
        assertTrue(item.getBody().contains("Investors rely on complete and accurate disclosures"));
        assertFalse(item.getBody().contains("Related Materials"));
        assertEquals("web:profile:sec", item.getExtractionMethod());
    }

    @Test
    void extractsChineseGovernmentPolicyArticle() {
        String url = "https://www.gov.cn/zhengce/2026-06/20/content_1234567.htm";
        RawItem item = extractor.extract(document(url, govCnHtml()), source(url));

        assertEquals("国务院关于推进高质量发展的政策措施", item.getTitle());
        assertTrue(item.getBody().contains("为贯彻落实党中央、国务院决策部署"));
        assertTrue(item.getBody().contains("强化宏观政策协同，稳定市场预期"));
        assertFalse(item.getBody().contains("责任编辑"));
        assertEquals(LocalDateTime.of(2026, 6, 20, 10, 0), item.getPublishedAt());
        assertEquals("web:profile:gov-cn", item.getExtractionMethod());
    }

    @Test
    void extractsSinaFinanceArticleAndRemovesRelatedLinks() {
        String url = "https://finance.sina.com.cn/stock/marketresearch/2026-06-20/doc-example.shtml";
        RawItem item = extractor.extract(document(url, sinaFinanceHtml()), source(url));

        assertEquals("市场资金继续流入高股息板块", item.getTitle());
        assertTrue(item.getSummary().contains("高股息板块受到资金关注"));
        assertTrue(item.getBody().contains("多家机构认为，市场风险偏好仍在修复"));
        assertTrue(item.getBody().contains("配置上关注现金流稳定、分红能力强的公司"));
        assertFalse(item.getBody().contains("热门推荐"));
        assertEquals("web:profile:sina-finance", item.getExtractionMethod());
    }

    @Test
    void doesNotApplyProfileToLookalikeHost() {
        String url = "https://federalreserve.gov.example.com/article";
        RawItem item = extractor.extract(document(url, federalReserveHtml()), source(url));

        assertEquals("web:generic-score", item.getExtractionMethod());
    }

    private Document document(String url, String html) {
        return Jsoup.parse(html, url);
    }

    private Source source(String url) {
        Source source = new Source();
        source.setType("WEB");
        source.setUrl(url);
        source.setName("网页");
        source.setTags("市场");
        return source;
    }

    private String federalReserveHtml() {
        return "<html><head>"
                + "<meta property=\"og:title\" content=\"Federal Reserve issues FOMC statement\">"
                + "<meta name=\"description\" content=\"The Federal Reserve issued the Federal Open Market Committee statement.\">"
                + "</head><body>"
                + "<main id=\"article\">"
                + "<h1>Federal Reserve issues FOMC statement</h1>"
                + "<p class=\"article__time\">June 17, 2026</p>"
                + "<div class=\"shareDL\">Share this page</div>"
                + "<p>The Federal Reserve issued the Federal Open Market Committee statement after its June meeting.</p>"
                + "<p>The Committee seeks to achieve maximum employment and inflation at the rate of 2 percent over the longer run.</p>"
                + "</main>"
                + "</body></html>";
    }

    private String secHtml() {
        return "<html><head>"
                + "<script type=\"application/ld+json\">{\"@type\":\"NewsArticle\",\"headline\":\"SEC Charges Company With Misleading Revenue Disclosures\","
                + "\"description\":\"The Securities and Exchange Commission today charged Example Corp. with misleading revenue disclosures.\","
                + "\"datePublished\":\"2026-06-12T14:30:00-04:00\"}</script>"
                + "</head><body>"
                + "<article class=\"article-content\">"
                + "<h1>SEC Charges Company With Misleading Revenue Disclosures</h1>"
                + "<div class=\"field--name-body\">"
                + "<p>The Securities and Exchange Commission today charged Example Corp. with misleading revenue disclosures.</p>"
                + "<p>Investors rely on complete and accurate disclosures when assessing public companies.</p>"
                + "</div>"
                + "<aside>Related Materials</aside>"
                + "</article>"
                + "</body></html>";
    }

    private String govCnHtml() {
        return "<html><head><title>国务院关于推进高质量发展的政策措施_政策_中国政府网</title>"
                + "<meta name=\"pubdate\" content=\"2026年06月20日 10:00\">"
                + "</head><body>"
                + "<h1>国务院关于推进高质量发展的政策措施</h1>"
                + "<div class=\"pages_content\">"
                + "<p>为贯彻落实党中央、国务院决策部署，现提出以下政策措施。</p>"
                + "<p>强化宏观政策协同，稳定市场预期，推动经济高质量发展。</p>"
                + "<p>责任编辑：张三</p>"
                + "</div>"
                + "</body></html>";
    }

    private String sinaFinanceHtml() {
        return "<html><head>"
                + "<meta property=\"og:title\" content=\"市场资金继续流入高股息板块\">"
                + "<meta property=\"og:description\" content=\"高股息板块受到资金关注，机构建议关注现金流质量。\">"
                + "<meta property=\"article:published_time\" content=\"2026-06-20T09:30:00+08:00\">"
                + "</head><body>"
                + "<h1 class=\"main-title\">市场资金继续流入高股息板块</h1>"
                + "<div id=\"artibody\">"
                + "<p>多家机构认为，市场风险偏好仍在修复，高股息资产具备防御和收益属性。</p>"
                + "<p>配置上关注现金流稳定、分红能力强的公司，同时跟踪政策变化。</p>"
                + "<div class=\"article-bottom\">热门推荐：相关阅读</div>"
                + "</div>"
                + "</body></html>";
    }
}
