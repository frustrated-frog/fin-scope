package com.finscope.rpc.source;

import com.finscope.domain.fetch.RawItem;
import com.finscope.domain.source.Source;
import com.finscope.rpc.acquisition.JdkAcquisitionRuntime;
import com.finscope.rpc.acquisition.RecordingAcquisitionRuntime;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSourceAdapterTest {
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void fetchesWebPageThroughArticleExtractor() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/newsevents/pressreleases/monetary20260617a.htm", exchange -> {
            byte[] bytes = html().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        server.start();

        Source source = new Source();
        source.setType("WEB");
        source.setUrl("http://localhost:" + server.getAddress().getPort()
                + "/newsevents/pressreleases/monetary20260617a.htm");

        RecordingAcquisitionRuntime runtime = new RecordingAcquisitionRuntime(new JdkAcquisitionRuntime());
        List<RawItem> items = new WebSourceAdapter(runtime, new WebArticleExtractor()).fetch(source);

        assertEquals(1, items.size());
        RawItem item = items.get(0);
        assertEquals("Federal Reserve issues FOMC statement", item.getTitle());
        assertTrue(item.getBody().contains("maximum employment"));
        assertEquals("web:generic-score", item.getExtractionMethod());
        assertEquals(1, runtime.getRequests().size());
        assertEquals("WEB_ARTICLE", runtime.getRequests().get(0).getPurpose());
    }

    @Test
    void prefersNextDataWhenVisibleDomIsJavascriptShell() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/dynamic", exchange -> {
            byte[] bytes = ("<html><head><title>加载中</title></head><body><div id=\"__next\"></div>"
                    + "<script id=\"__NEXT_DATA__\" type=\"application/json\">"
                    + "{\"props\":{\"pageProps\":{\"article\":{"
                    + "\"title\":\"动态财经正文\","
                    + "\"content\":\"这是一篇通过页面内嵌状态恢复的财经正文，包含足够长度的研究信息。\"}}}}"
                    + "</script></body></html>").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        server.start();
        Source source = new Source();
        source.setType("WEB");
        source.setUrl("http://localhost:" + server.getAddress().getPort() + "/dynamic");

        RawItem item = new WebSourceAdapter().fetch(source).get(0);

        assertEquals("动态财经正文", item.getTitle());
        assertEquals("web:embedded-next-data", item.getExtractionMethod());
    }

    private String html() {
        return "<html><head>"
                + "<meta property=\"og:title\" content=\"Federal Reserve issues FOMC statement\">"
                + "</head><body>"
                + "<main><h1>Federal Reserve issues FOMC statement</h1>"
                + "<p>The Federal Reserve issued the Federal Open Market Committee statement.</p>"
                + "<p>The Committee seeks maximum employment and price stability over the longer run.</p>"
                + "</main>"
                + "</body></html>";
    }
}
