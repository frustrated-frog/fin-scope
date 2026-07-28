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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebListSourceAdapterTest {
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void extractsArticleLinksFromListPageAndReturnsRawItems() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/list", exchange -> {
            byte[] bytes = ("<!doctype html><html><body><main>"
                    + "<h2><a href=\"/a\">第一篇宏观文章</a></h2>"
                    + "<h2><a href=\"/b\">第二篇 AI 文章</a></h2>"
                    + "<nav><a href=\"/about\">关于我们</a></nav>"
                    + "</main></body></html>").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        server.createContext("/a", exchange -> write(exchange,
                "<!doctype html><html><head><title>第一篇宏观文章</title></head><body><article><h1>第一篇宏观文章</h1><p>美联储释放降息信号，黄金继续走强。</p></article></body></html>"));
        server.createContext("/b", exchange -> write(exchange,
                "<!doctype html><html><head><title>第二篇 AI 文章</title></head><body><article><h1>第二篇 AI 文章</h1><p>AI Agent 工作流正在改变信息处理方式。</p></article></body></html>"));
        server.start();

        Source source = new Source();
        source.setName("列表页");
        source.setType("WEB_LIST");
        source.setUrl("http://localhost:" + server.getAddress().getPort() + "/list");

        RecordingAcquisitionRuntime runtime = new RecordingAcquisitionRuntime(new JdkAcquisitionRuntime());
        List<RawItem> items = new WebListSourceAdapter(runtime, new WebArticleExtractor()).fetch(source);

        assertEquals(2, items.size());
        assertTrue(items.get(0).getTitle().contains("第一篇宏观文章"));
        assertTrue(items.get(0).getUrl().endsWith("/a"));
        assertTrue(items.get(1).getTitle().contains("第二篇 AI 文章"));
        assertTrue(items.get(1).getUrl().endsWith("/b"));
        assertEquals(3, runtime.getRequests().size());
        assertEquals("WEB_LIST", runtime.getRequests().get(0).getPurpose());
        assertEquals("WEB_DETAIL", runtime.getRequests().get(1).getPurpose());
        assertEquals("WEB_DETAIL", runtime.getRequests().get(2).getPurpose());
    }

    @Test
    void respectsMaxItemsPerRunBeforeFetchingArticlePages() throws Exception {
        AtomicBoolean secondArticleVisited = new AtomicBoolean(false);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/list", exchange -> {
            byte[] bytes = ("<!doctype html><html><body><main>"
                    + "<h2><a href=\"/a\">第一篇宏观文章</a></h2>"
                    + "<h2><a href=\"/b\">第二篇 AI 文章</a></h2>"
                    + "</main></body></html>").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        server.createContext("/a", exchange -> write(exchange,
                "<!doctype html><html><head><title>第一篇宏观文章</title></head><body><article><h1>第一篇宏观文章</h1><p>美联储释放降息信号，黄金继续走强。</p></article></body></html>"));
        server.createContext("/b", exchange -> {
            secondArticleVisited.set(true);
            write(exchange, "<!doctype html><html><head><title>第二篇 AI 文章</title></head><body><article><h1>第二篇 AI 文章</h1><p>AI Agent 工作流正在改变信息处理方式。</p></article></body></html>");
        });
        server.start();

        Source source = new Source();
        source.setName("列表页");
        source.setType("WEB_LIST");
        source.setUrl("http://localhost:" + server.getAddress().getPort() + "/list");
        source.setMaxItemsPerRun(1);

        List<RawItem> items = new WebListSourceAdapter().fetch(source);

        assertEquals(1, items.size());
        assertTrue(items.get(0).getUrl().endsWith("/a"));
        assertFalse(secondArticleVisited.get());
    }

    private void write(com.sun.net.httpserver.HttpExchange exchange, String html) throws java.io.IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(bytes);
        }
    }
}
