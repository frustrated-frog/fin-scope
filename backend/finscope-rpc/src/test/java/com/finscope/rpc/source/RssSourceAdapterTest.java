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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RssSourceAdapterTest {
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void parsesArxivRssIntoCleanArticleContent() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/rss", exchange -> {
            byte[] bytes = arxivRss().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/rss+xml; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        server.start();

        Source source = new Source();
        source.setType("RSS");
        source.setUrl("http://localhost:" + server.getAddress().getPort() + "/rss");

        List<RawItem> items = new RssSourceAdapter().fetch(source);

        assertEquals(1, items.size());
        RawItem item = items.get(0);
        assertEquals("RIFT-Bench: Dynamic Red-teaming For Agentic AI Systems", item.getTitle());
        assertEquals("https://arxiv.org/abs/2606.23927", item.getUrl());
        assertTrue(item.getSummary().contains("Agentic AI systems powered by large language models"));
        assertTrue(item.getBody().contains("作者：Yarin Yerushalmi Levi"));
        assertTrue(item.getBody().contains("分类：cs.AI"));
        assertTrue(item.getBody().contains("摘要：arXiv:2606.23927v1"));
        assertFalse(item.getBody().contains("<description>"));
        assertEquals("RSS_ITEM", item.getContentType());
        assertEquals("rss:rome", item.getExtractionMethod());
        assertTrue(item.getQualityScore() >= 80);
    }

    private String arxivRss() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rss xmlns:dc=\"http://purl.org/dc/elements/1.1/\" version=\"2.0\"><channel>"
                + "<title>cs.AI updates on arXiv.org</title>"
                + "<item>"
                + "<title>RIFT-Bench: Dynamic Red-teaming For Agentic AI Systems</title>"
                + "<link>https://arxiv.org/abs/2606.23927</link>"
                + "<description>arXiv:2606.23927v1 Announce Type: new\n"
                + "Abstract: Agentic AI systems powered by large language models are rapidly evolving into autonomous decision-making systems.</description>"
                + "<guid isPermaLink=\"false\">oai:arXiv.org:2606.23927v1</guid>"
                + "<category>cs.AI</category>"
                + "<pubDate>Wed, 24 Jun 2026 00:00:00 -0400</pubDate>"
                + "<dc:creator>Yarin Yerushalmi Levi, Roy Betser</dc:creator>"
                + "</item></channel></rss>";
    }
}
