package com.finscope.rpc.source;

import com.finscope.domain.fetch.RawItem;
import com.finscope.domain.source.Source;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XPostSourceAdapterTest {
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void fetchesXStatusArticleFromPublicJsonAdapter() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/justloveabit/status/2069292114794762335", exchange -> {
            byte[] bytes = fxTwitterArticleJson().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        server.start();

        Source source = new Source();
        source.setType("WEB");
        source.setUrl("https://x.com/justloveabit/status/2069292114794762335");

        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        List<RawItem> items = new XPostSourceAdapter(baseUrl, baseUrl).fetch(source);

        assertEquals(1, items.size());
        RawItem item = items.get(0);
        assertEquals("Cloudflare“赛博菩萨”的免费额度，白嫖了一整套互联网基础设施", item.getTitle());
        assertEquals("https://x.com/justloveabit/status/2069292114794762335", item.getUrl());
        assertTrue(item.getSummary().contains("过去半年"));
        assertTrue(item.getBody().contains("Workers 写后端"));
        assertTrue(item.getBody().contains("Serverless 操作系统"));
        assertTrue(item.getBody().contains("作者：loveabit(@justloveabit)"));
        assertEquals("SOCIAL_POST", item.getContentType());
        assertEquals("x:fxtwitter:article", item.getExtractionMethod());
        assertTrue(item.getQualityScore() >= 90);
    }

    private String fxTwitterArticleJson() {
        return "{"
                + "\"code\":200,"
                + "\"tweet\":{"
                + "\"url\":\"https://x.com/justloveabit/status/2069292114794762335\","
                + "\"id\":\"2069292114794762335\","
                + "\"text\":\"\","
                + "\"author\":{\"screen_name\":\"justloveabit\",\"name\":\"loveabit\"},"
                + "\"likes\":171,\"retweets\":33,\"replies\":1,\"views\":19490,"
                + "\"created_at\":\"Tue Jun 23 05:31:00 +0000 2026\","
                + "\"article\":{"
                + "\"id\":\"2069289021814001666\","
                + "\"title\":\"Cloudflare“赛博菩萨”的免费额度，白嫖了一整套互联网基础设施\","
                + "\"preview_text\":\"你听说过 Cloudflare 吗？\\n过去半年，我没买过一台服务器，没付过一分钱云费用。\","
                + "\"content\":{\"blocks\":["
                + "{\"text\":\"你听说过 Cloudflare 吗？\"},"
                + "{\"text\":\"过去半年，我没买过一台服务器，没付过一分钱云费用，却跑着一整套互联网基础设施。\"},"
                + "{\"text\":\"比如：用 Workers 写后端 → 用 D1 做数据库 → 用 R2 存文件 → 用 Pages 托管前端 → 用 KV 做缓存。\"},"
                + "{\"text\":\"Cloudflare 已经不是 CDN 公司了，它是一个 全球部署的 Serverless 操作系统。\"}"
                + "]}"
                + "}"
                + "}"
                + "}";
    }
}
