package com.finscope.web;

import com.finscope.rpc.llm.LlmChatClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "finscope.data-root=target/test-data/api",
        "spring.datasource.url=jdbc:sqlite:target/test-data/api/finance.db"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FinScopeApiIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private LlmChatClient llmChatClient;

    private HttpServer server;
    private String rssUrl;

    @BeforeEach
    void setUp() throws Exception {
        deleteIfExists("topic_article");
        deleteIfExists("topic_brief");
        deleteIfExists("insight_card");
        jdbcTemplate.update("DELETE FROM agent_run");
        jdbcTemplate.update("DELETE FROM brief");
        jdbcTemplate.update("DELETE FROM article");
        jdbcTemplate.update("DELETE FROM fetch_run");
        jdbcTemplate.update("DELETE FROM source");
        jdbcTemplate.update("DELETE FROM topic");
        jdbcTemplate.update("DELETE FROM sqlite_sequence WHERE name IN ('agent_run','brief','article','fetch_run','source','topic','insight_card')");
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/rss", exchange -> {
            byte[] bytes = rss().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/rss+xml; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        server.createContext("/article", exchange -> {
            byte[] bytes = htmlArticle().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        server.createContext("/cloudflare", exchange -> {
            byte[] bytes = cloudflareArticle().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        server.createContext("/x-shell", exchange -> {
            byte[] bytes = dynamicShell().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        server.start();
        rssUrl = "http://localhost:" + server.getAddress().getPort() + "/rss";
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sourceFetchInboxDedupeAndBriefFlowWorks() throws Exception {
        String sourceJson = "{\"name\":\"测试财经RSS\",\"type\":\"RSS\",\"url\":\"" + rssUrl + "\",\"enabled\":true,"
                + "\"fetchFrequencyMinutes\":60,\"credibility\":4,\"tags\":\"宏观,市场\"}";

        mvc.perform(post("/api/sources").contentType("application/json").content(sourceJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("测试财经RSS"));

        mvc.perform(post("/api/sources/1/fetch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(1));

        mvc.perform(get("/api/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$[0].title").value("美联储释放降息信号 黄金走强"))
                .andExpect(jsonPath("$[0].noveltyType").value("NEW"));

        mvc.perform(post("/api/sources/1/fetch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicateCount").value(greaterThanOrEqualTo(1)));

        mvc.perform(post("/api/briefs/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.briefDate").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.markdownPath", containsString("daily-briefs")));

        mvc.perform(get("/api/briefs/" + LocalDate.now()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", containsString("美联储释放降息信号")));
    }

    @Test
    void manualUrlCanBeIngestedAsInsightCardAndUsedByBrief() throws Exception {
        String articleUrl = "http://localhost:" + server.getAddress().getPort() + "/article";

        mvc.perform(post("/api/articles/ingest-url")
                        .contentType("application/json")
                        .content("{\"url\":\"" + articleUrl + "\",\"sourceName\":\"手动研究\",\"tags\":\"宏观,黄金\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.article.title").value("美联储暗示降息 黄金ETF获得资金流入"))
                .andExpect(jsonPath("$.article.sourceName").value("手动研究"))
                .andExpect(jsonPath("$.insightCard.oneSentenceSummary", containsString("美联储")))
                .andExpect(jsonPath("$.insightCard.coreEvent", containsString("降息")))
                .andExpect(jsonPath("$.insightCard.importance", containsString("利率预期")))
                .andExpect(jsonPath("$.insightCard.impactTargets", containsString("黄金")))
                .andExpect(jsonPath("$.insightCard.cardMarkdown", containsString("### 后续观察")))
                .andExpect(jsonPath("$.insightCard.cardMarkdown", containsString("### 可沉淀主题")))
                .andExpect(jsonPath("$.insightCard.cardMarkdown", containsString("解读来源：FALLBACK")));

        mvc.perform(get("/api/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].insightCard.coreEvent", containsString("降息")));

        mvc.perform(get("/api/agent-runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nodeName").value("article-interpret"))
                .andExpect(jsonPath("$[0].status").value("FALLBACK"))
                .andExpect(jsonPath("$[0].output", containsString("FALLBACK")));

        mvc.perform(post("/api/briefs/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", containsString("### 核心事件")))
                .andExpect(jsonPath("$.content", containsString("### 后续观察")));
    }

    @Test
    void configuredAgentInterpretsArticleCardsTopicsAndTrace() throws Exception {
        when(llmChatClient.isConfigured()).thenReturn(true);
        when(llmChatClient.modelName()).thenReturn("fake-openai-model");
        when(llmChatClient.complete(anyString(), anyString())).thenReturn(cloudflareInterpretationJson());
        String articleUrl = "http://localhost:" + server.getAddress().getPort() + "/cloudflare";

        mvc.perform(post("/api/articles/ingest-url")
                        .contentType("application/json")
                        .content("{\"url\":\"" + articleUrl + "\",\"sourceName\":\"技术观察\",\"tags\":\"技术工具\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insightCard.oneSentenceSummary", containsString("Cloudflare 免费额度")))
                .andExpect(jsonPath("$.insightCard.importance", containsString("低成本独立开发")))
                .andExpect(jsonPath("$.insightCard.cardMarkdown", containsString("### 可沉淀主题")))
                .andExpect(jsonPath("$.insightCard.cardMarkdown", containsString("Cloudflare 免费基础设施实践")));

        mvc.perform(post("/api/topics/from-article/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cloudflare 免费基础设施实践"))
                .andExpect(jsonPath("$.terms", containsString("Workers")))
                .andExpect(jsonPath("$.terms", containsString("D1")))
                .andExpect(jsonPath("$.learningQuestions", containsString("免费额度")))
                .andExpect(jsonPath("$.learningQuestions", containsString("供应商锁定")));

        mvc.perform(get("/api/agent-runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nodeName").value("article-interpret"))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$[0].input", containsString("fake-openai-model")))
                .andExpect(jsonPath("$[0].output", containsString("Cloudflare 免费基础设施实践")));
    }

    @Test
    void manualUrlRejectsUnreadableDynamicShell() throws Exception {
        String shellUrl = "http://localhost:" + server.getAddress().getPort() + "/x-shell";

        mvc.perform(post("/api/articles/ingest-url")
                        .contentType("application/json")
                        .content("{\"url\":\"" + shellUrl + "\",\"sourceName\":\"手动研究\",\"tags\":\"市场\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("未能读取到可用正文")));
    }

    @Test
    void invalidManualUrlReturnsStructuredErrorWithRequestId() throws Exception {
        mvc.perform(post("/api/articles/ingest-url")
                        .header("X-Request-Id", "test-trace-001")
                        .contentType("application/json")
                        .content("{\"url\":\"ftp://example.com/article\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "test-trace-001"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message", containsString("Only http/https URL is supported")))
                .andExpect(jsonPath("$.error", containsString("Only http/https URL is supported")))
                .andExpect(jsonPath("$.traceId").value("test-trace-001"))
                .andExpect(jsonPath("$.path").value("/api/articles/ingest-url"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void missingTopicReturnsNotFoundErrorCodeAndGeneratedRequestId() throws Exception {
        mvc.perform(get("/api/topics/9999"))
                .andExpect(status().isNotFound())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message", containsString("Topic not found: 9999")))
                .andExpect(jsonPath("$.error", containsString("Topic not found: 9999")))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/topics/9999"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void exportPackageContainsManifestAndVault() throws Exception {
        mvc.perform(post("/api/briefs/generate"))
                .andExpect(status().isOk());

        String response = mvc.perform(post("/api/exports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path", containsString("backup-")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String path = response.replaceAll(".*\"path\":\"([^\"]+)\".*", "$1");
        try (ZipFile zipFile = new ZipFile(path)) {
            assertNotNull(zipFile.getEntry("manifest.json"));
            assertNotNull(zipFile.getEntry("vault/daily-briefs/" + LocalDate.now() + ".md"));
        }
    }

    @Test
    void corsAllowsFallbackViteDevPort() throws Exception {
        mvc.perform(get("/api/sources").header("Origin", "http://localhost:5174"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5174"));
    }

    @Test
    void articleAndBriefCanBeCompoundedIntoTopicNotes() throws Exception {
        String sourceJson = "{\"name\":\"测试财经RSS\",\"type\":\"RSS\",\"url\":\"" + rssUrl + "\",\"enabled\":true,"
                + "\"fetchFrequencyMinutes\":60,\"credibility\":4,\"tags\":\"宏观,市场\"}";

        mvc.perform(post("/api/sources").contentType("application/json").content(sourceJson))
                .andExpect(status().isOk());
        mvc.perform(post("/api/sources/1/fetch"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/briefs/generate"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/topics/from-article/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("美联储"))
                .andExpect(jsonPath("$.articleCount").value(1))
                .andExpect(jsonPath("$.terms", containsString("美联储")))
                .andExpect(jsonPath("$.learningQuestions", containsString("美联储")))
                .andExpect(jsonPath("$.markdownPath", containsString("vault/topics")));

        mvc.perform(post("/api/topics/from-brief/" + LocalDate.now()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)));

        mvc.perform(get("/api/topics/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topic.name").value("美联储"))
                .andExpect(jsonPath("$.linkedArticles[0].title").value("美联储释放降息信号 黄金走强"))
                .andExpect(jsonPath("$.linkedBriefs.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.markdown", containsString("美联储")))
                .andExpect(jsonPath("$.markdown", containsString("### 核心事件")))
                .andExpect(jsonPath("$.markdown", containsString("### 后续观察")));

        mvc.perform(post("/api/topics/1/notes")
                        .contentType("application/json")
                        .content("{\"status\":\"REVIEWING\",\"note\":\"我理解的核心变量是利率预期。\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEWING"))
                .andExpect(jsonPath("$.markdownPath", containsString("vault/topics")));
        boolean noteWritten = Files.walk(Paths.get("target/test-data/api/vault/topics"))
                .filter(Files::isRegularFile)
                .anyMatch(path -> {
                    try {
                        return new String(Files.readAllBytes(path), "UTF-8").contains("我理解的核心变量是利率预期。");
                    } catch (Exception ex) {
                        return false;
                    }
                });
        assertTrue(noteWritten);
    }

    private void deleteIfExists(String table) {
        try {
            jdbcTemplate.update("DELETE FROM " + table);
        } catch (Exception ignored) {
            // Older schemas do not have V3 link tables before the initializer runs.
        }
    }

    private String rss() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rss version=\"2.0\"><channel><title>Test Feed</title>"
                + "<item>"
                + "<title>美联储释放降息信号 黄金走强</title>"
                + "<link>https://example.com/fed-gold</link>"
                + "<description>宏观市场关注美联储降息预期，黄金价格继续走强。</description>"
                + "<pubDate>Tue, 23 Jun 2026 09:00:00 GMT</pubDate>"
                + "</item>"
                + "</channel></rss>";
    }

    private String htmlArticle() {
        return "<!doctype html><html><head>"
                + "<title>美联储暗示降息 黄金ETF获得资金流入</title>"
                + "<meta name=\"description\" content=\"美联储官员释放偏鸽信号，黄金ETF出现连续资金流入。\">"
                + "</head><body><article>"
                + "<h1>美联储暗示降息 黄金ETF获得资金流入</h1>"
                + "<p>美联储官员释放偏鸽信号，市场开始交易降息预期。</p>"
                + "<p>黄金ETF出现连续资金流入，美元指数回落，投资者关注下一次议息会议。</p>"
                + "</article></body></html>";
    }

    private String cloudflareArticle() {
        return "<!doctype html><html><head>"
                + "<title>Cloudflare“赛博菩萨”的免费额度，白嫖了一整套互联网基础设施</title>"
                + "<meta name=\"description\" content=\"用 Workers、D1、R2、Pages、KV 组合低成本搭建个人项目。\">"
                + "</head><body><article>"
                + "<h1>Cloudflare“赛博菩萨”的免费额度，白嫖了一整套互联网基础设施</h1>"
                + "<p>过去半年，我没买过一台服务器，没付过一分钱云费用，却跑着一整套互联网基础设施。</p>"
                + "<p>用 Workers 写后端，用 D1 做数据库，用 R2 存文件，用 Pages 托管前端，用 KV 做缓存。</p>"
                + "<p>Cloudflare 已经不是 CDN 公司了，它是一个全球部署的 Serverless 操作系统。</p>"
                + "</article></body></html>";
    }

    private String cloudflareInterpretationJson() {
        return "{"
                + "\"contentType\":\"TECH_PRACTICE\","
                + "\"topicName\":\"Cloudflare 免费基础设施实践\","
                + "\"topicDescription\":\"拆解 Cloudflare Workers、D1、R2、Pages、KV 如何组成低成本 Serverless 基础设施。\","
                + "\"oneSentenceSummary\":\"这篇长文把 Cloudflare 免费额度串成一套可落地的个人项目基础设施。\","
                + "\"coreEvent\":\"作者用 Workers、D1、R2、Pages 和 KV 组合替代传统服务器与对象存储。\","
                + "\"importance\":\"它提供了低成本独立开发和内容产品验证的工程样本。\","
                + "\"impactTargets\":[\"Cloudflare\",\"Workers\",\"D1\",\"R2\",\"Pages\",\"KV\"],"
                + "\"keyTerms\":[\"Cloudflare\",\"Workers\",\"D1\",\"R2\",\"Pages\",\"KV\",\"Serverless\"],"
                + "\"learningQuestions\":[\"免费额度的边界和限制是什么？\",\"这套部署方案适合哪些个人项目？\",\"长期迁移和供应商锁定风险如何控制？\"],"
                + "\"confidence\":0.91"
                + "}";
    }

    private String dynamicShell() {
        return "<!doctype html><html><head><title>X</title></head><body>"
                + "<noscript>JavaScript is not available.</noscript>"
                + "<main>We have detected that JavaScript is disabled in this browser. "
                + "Please enable JavaScript or switch to a supported browser to continue using x.com.</main>"
                + "</body></html>";
    }
}
