package com.finscope.rpc.source;

import com.finscope.domain.fetch.RawItem;
import com.finscope.domain.source.Source;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddedDataExtractorTest {

    @Test
    void extractsArticleFromNextDataWhenDomIsOnlyJavascriptShell() {
        Document document = Jsoup.parse("<html><head><title>加载中</title></head><body>"
                + "<div id=\"__next\"></div>"
                + "<script id=\"__NEXT_DATA__\" type=\"application/json\">"
                + "{\"props\":{\"pageProps\":{\"article\":{"
                + "\"title\":\"政策推动长期资金入市\","
                + "\"content\":\"长期资金配置权益资产的比例正在稳步提升，市场结构将继续改善。\","
                + "\"publishedAt\":\"2026-07-28T09:30:00+08:00\"}}}}"
                + "</script></body></html>", "https://example.com/article/1");
        Source source = source();

        Optional<RawItem> result = new EmbeddedDataExtractor().extract(document, source);

        assertTrue(result.isPresent());
        assertEquals("政策推动长期资金入市", result.get().getTitle());
        assertTrue(result.get().getBody().contains("市场结构"));
        assertEquals("web:embedded-next-data", result.get().getExtractionMethod());
    }

    @Test
    void ignoresEmbeddedConfigurationWithoutArticleBody() {
        Document document = Jsoup.parse("<script id=\"__NEXT_DATA__\" type=\"application/json\">"
                + "{\"buildId\":\"abc\",\"runtimeConfig\":{\"locale\":\"zh-CN\"}}"
                + "</script>", "https://example.com/empty");

        assertFalse(new EmbeddedDataExtractor().extract(document, source()).isPresent());
    }

    @Test
    void extractsArticleBodyFromJsonLdGraph() {
        Document document = Jsoup.parse("<html><body><div id=\"app\"></div>"
                + "<script type=\"application/ld+json\">{\"@graph\":[{"
                + "\"@type\":\"NewsArticle\",\"headline\":\"上市公司发布年度报告\","
                + "\"articleBody\":\"公司营业收入保持增长，经营现金流显著改善，管理层同时提示外部需求波动风险。\","
                + "\"datePublished\":\"2026-07-28T10:00:00+08:00\"}]}"
                + "</script></body></html>", "https://example.com/report");

        Optional<RawItem> result = new EmbeddedDataExtractor().extract(document, source());

        assertTrue(result.isPresent());
        assertEquals("上市公司发布年度报告", result.get().getTitle());
        assertEquals("web:embedded-json-ld", result.get().getExtractionMethod());
    }

    private Source source() {
        Source source = new Source();
        source.setName("测试站点");
        source.setType("WEB");
        source.setUrl("https://example.com/article/1");
        return source;
    }
}
