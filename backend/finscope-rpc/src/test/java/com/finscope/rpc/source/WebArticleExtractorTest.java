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
    void extractsBeaReleaseFromOfficialProfile() {
        String url = "https://www.bea.gov/news/2026/gross-domestic-product-second-quarter-2026";
        RawItem item = extractor.extract(document(url, officialArticleHtml(
                "U.S. GDP Increased in the Second Quarter",
                "Real gross domestic product increased at an annual rate in the second quarter.",
                "Real gross domestic product increased at an annual rate in the second quarter of 2026.",
                "Consumer spending and private investment contributed to the increase.",
                "2026-07-30T08:30:00-04:00")), source(url));

        assertEquals("U.S. GDP Increased in the Second Quarter", item.getTitle());
        assertTrue(item.getBody().contains("Consumer spending and private investment contributed"));
        assertEquals("web:profile:bea", item.getExtractionMethod());
    }

    @Test
    void extractsBankOfEnglandReleaseFromOfficialProfile() {
        String url = "https://www.bankofengland.co.uk/news/2026/june/monetary-policy-summary";
        RawItem item = extractor.extract(document(url, officialArticleHtml(
                "Monetary Policy Summary",
                "The Monetary Policy Committee voted to maintain Bank Rate.",
                "The Monetary Policy Committee voted to maintain Bank Rate at its latest meeting.",
                "The Committee will continue to monitor inflationary pressures closely.",
                "2026-06-18T12:00:00Z")), source(url));

        assertEquals("Monetary Policy Summary", item.getTitle());
        assertTrue(item.getBody().contains("monitor inflationary pressures closely"));
        assertEquals("web:profile:bank-of-england", item.getExtractionMethod());
    }

    @Test
    void extractsChineseRegulatorArticleFromOfficialProfile() {
        String url = "https://www.csrc.gov.cn/csrc/c100028/c7890123/content.shtml";
        RawItem item = extractor.extract(document(url, chineseOfficialHtml()), source(url));

        assertEquals("证监会发布资本市场高质量发展举措", item.getTitle());
        assertTrue(item.getBody().contains("进一步完善资本市场基础制度"));
        assertTrue(item.getBody().contains("提升上市公司质量"));
        assertEquals("web:profile:csrc", item.getExtractionMethod());
    }

    @Test
    void extractsTonghuashunArticleFromFinanceProfile() {
        String url = "https://www.10jqka.com.cn/20260620/c123456789.shtml";
        RawItem item = extractor.extract(document(url, tonghuashunHtml()), source(url));

        assertEquals("A股市场延续震荡修复", item.getTitle());
        assertTrue(item.getBody().contains("市场成交保持活跃"));
        assertFalse(item.getBody().contains("同花顺财经"));
        assertEquals("web:profile:tonghuashun", item.getExtractionMethod());
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

    private String officialArticleHtml(String title, String summary, String firstParagraph, String secondParagraph, String date) {
        return "<html><head>"
                + "<meta property=\"og:title\" content=\"" + title + "\">"
                + "<meta name=\"description\" content=\"" + summary + "\">"
                + "<meta property=\"article:published_time\" content=\"" + date + "\">"
                + "</head><body>"
                + "<header>Global navigation</header>"
                + "<main class=\"article-content\">"
                + "<h1>" + title + "</h1>"
                + "<p>" + firstParagraph + "</p>"
                + "<p>" + secondParagraph + "</p>"
                + "<aside>Related content</aside>"
                + "</main>"
                + "</body></html>";
    }

    private String chineseOfficialHtml() {
        return "<html><head>"
                + "<meta name=\"PubDate\" content=\"2026年06月20日 09:15\">"
                + "</head><body>"
                + "<h1>证监会发布资本市场高质量发展举措</h1>"
                + "<div class=\"TRS_Editor\">"
                + "<p>进一步完善资本市场基础制度，加强投资者保护。</p>"
                + "<p>提升上市公司质量，促进市场平稳健康发展。</p>"
                + "</div>"
                + "</body></html>";
    }

    private String tonghuashunHtml() {
        return "<html><head>"
                + "<meta property=\"og:title\" content=\"A股市场延续震荡修复\">"
                + "<meta name=\"description\" content=\"市场成交保持活跃，资金关注政策和业绩主线。\">"
                + "</head><body>"
                + "<h1>A股市场延续震荡修复</h1>"
                + "<div class=\"article-content\">"
                + "<p>市场成交保持活跃，资金继续围绕政策预期和业绩确定性展开。</p>"
                + "<p>机构认为后续需要关注宏观数据和流动性变化。</p>"
                + "<div>同花顺财经 责任编辑</div>"
                + "</div>"
                + "</body></html>";
    }
}
