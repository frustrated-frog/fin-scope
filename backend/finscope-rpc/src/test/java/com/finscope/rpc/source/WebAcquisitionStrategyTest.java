package com.finscope.rpc.source;

import com.finscope.domain.fetch.RawItem;
import com.finscope.domain.source.Source;
import com.finscope.rpc.acquisition.AcquisitionErrorType;
import com.finscope.rpc.acquisition.AcquisitionException;
import com.finscope.rpc.acquisition.AcquisitionRequest;
import com.finscope.rpc.acquisition.AcquisitionResponse;
import com.finscope.rpc.acquisition.BrowserFetcher;
import com.finscope.rpc.acquisition.DisabledBrowserFetcher;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebAcquisitionStrategyTest {

    @Test
    void keepsHighQualityStaticPageOnHttpPath() {
        AtomicInteger browserCalls = new AtomicInteger();
        BrowserFetcher browser = request -> {
            browserCalls.incrementAndGet();
            return response(request, "<article><h1>不应访问</h1></article>");
        };
        WebAcquisitionStrategy strategy = strategy(staticHtml(), browser);

        RawItem item = strategy.fetch(source());

        assertEquals("静态财经文章", item.getTitle());
        assertTrue(item.getBody().contains("稳定可靠"));
        assertEquals(0, browserCalls.get());
    }

    @Test
    void escalatesJavascriptShellToBrowserAndMarksExtractionPath() {
        AtomicInteger browserCalls = new AtomicInteger();
        BrowserFetcher browser = request -> {
            browserCalls.incrementAndGet();
            return response(request, "<html><body><article><h1>渲染后的研究正文</h1>"
                    + "<p>浏览器执行脚本后获得完整正文，包含行业供需、公司经营和风险提示。</p>"
                    + "<p>这些信息足以进入后续研究与去重流程，并保留稳定的抽取证据。</p>"
                    + "</article></body></html>");
        };
        WebAcquisitionStrategy strategy = strategy(javascriptShell(), browser);

        RawItem item = strategy.fetch(source());

        assertEquals(1, browserCalls.get());
        assertEquals("渲染后的研究正文", item.getTitle());
        assertTrue(item.getExtractionMethod().startsWith("web:browser:"));
    }

    @Test
    void classifiesJavascriptShellWhenBrowserIsDisabled() {
        WebAcquisitionStrategy strategy = strategy(javascriptShell(), new DisabledBrowserFetcher());

        AcquisitionException error = assertThrows(AcquisitionException.class,
                () -> strategy.fetch(source()));

        assertEquals(AcquisitionErrorType.BROWSER_UNAVAILABLE, error.getErrorType());
        assertEquals(false, error.isRetryable());
    }

    private WebAcquisitionStrategy strategy(String html, BrowserFetcher browserFetcher) {
        return new WebAcquisitionStrategy(request -> response(request, html), browserFetcher,
                new WebArticleExtractor(), new EmbeddedDataExtractor());
    }

    private AcquisitionResponse response(AcquisitionRequest request, String html) {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        return new AcquisitionResponse(request.getUri(), request.getUri(), 200,
                Collections.<String, String>emptyMap(), bytes, html,
                "text/html; charset=utf-8", "UTF-8", "test-hash", 1, 5L, Instant.now());
    }

    private Source source() {
        Source source = new Source();
        source.setName("测试财经站点");
        source.setType("WEB");
        source.setUrl("https://example.com/article");
        return source;
    }

    private String javascriptShell() {
        return "<html><head><title>加载中</title></head><body><div id=\"app\"></div>"
                + "<noscript>请启用 JavaScript 后继续访问</noscript>"
                + "<script src=\"/assets/app.js\"></script></body></html>";
    }

    private String staticHtml() {
        return "<html><body><article><h1>静态财经文章</h1>"
                + "<p>统一采集运行时让网页抓取更加稳定可靠，并保留完整审计信息。</p>"
                + "<p>正文抽取通过质量评分选择主要内容，避免导航和广告噪声。</p>"
                + "</article></body></html>";
    }
}
