package com.finscope.web;

import com.finscope.rpc.llm.LlmChatClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.annotation.Resource;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    @Resource
    private MockMvc mvc;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private LlmChatClient llmChatClient;

    private HttpServer server;
    private String rssUrl;

    @BeforeEach
    void setUp() throws Exception {
        deleteIfExists("topic_article");
        deleteIfExists("topic_brief");
        deleteIfExists("content_idea");
        deleteIfExists("learning_task");
        deleteIfExists("evidence_item");
        deleteIfExists("event_article_link");
        deleteIfExists("event_cluster");
        deleteIfExists("research_run_plan");
        deleteIfExists("research_run");
        deleteIfExists("intake_candidate");
        deleteIfExists("fetch_batch");
        deleteIfExists("insight_card");
        deleteIfExists("async_task");
        jdbcTemplate.update("DELETE FROM agent_run");
        jdbcTemplate.update("DELETE FROM brief");
        jdbcTemplate.update("DELETE FROM article");
        jdbcTemplate.update("DELETE FROM fetch_run");
        jdbcTemplate.update("DELETE FROM source");
        jdbcTemplate.update("DELETE FROM topic");
        jdbcTemplate.update("DELETE FROM sqlite_sequence WHERE name IN "
                + "('agent_run','brief','article','fetch_run','source','topic','insight_card',"
                + "'event_cluster','evidence_item','learning_task','content_idea','research_run','research_run_plan',"
                + "'fetch_batch','intake_candidate')");
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/rss", exchange -> {
            byte[] bytes = rss().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/rss+xml; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        server.createContext("/empty-rss", exchange -> {
            byte[] bytes = emptyRss().getBytes(StandardCharsets.UTF_8);
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
        server.createContext("/fed-followup", exchange -> {
            byte[] bytes = fedFollowUpArticle().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        server.createContext("/pboc-policy", exchange -> {
            byte[] bytes = pbocPolicyArticle().getBytes(StandardCharsets.UTF_8);
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
    void legacySourceFetchRoutesToIntakeAndDoesNotCreateArticles() throws Exception {
        String sourceJson = "{\"name\":\"测试财经RSS\",\"type\":\"RSS\",\"url\":\"" + rssUrl + "\",\"enabled\":true,"
                + "\"fetchFrequencyMinutes\":60,\"credibility\":4,\"tags\":\"宏观,市场\"}";

        mvc.perform(post("/api/sources").contentType("application/json").content(sourceJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("测试财经RSS"));

        mvc.perform(post("/api/sources/1/fetch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.candidateCount").value(1));

        mvc.perform(get("/api/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(get("/api/intake/candidates?status=PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].originalTitle").value("美联储释放降息信号 黄金走强"));

        mvc.perform(post("/api/sources/1/fetch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicateCount").value(greaterThanOrEqualTo(1)));

        mvc.perform(get("/api/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void sourceFetchPromoteThenBriefFlowWorks() throws Exception {
        String sourceJson = "{\"name\":\"测试财经RSS\",\"type\":\"RSS\",\"url\":\"" + rssUrl + "\",\"enabled\":true,"
                + "\"fetchFrequencyMinutes\":60,\"credibility\":4,\"tags\":\"宏观,市场\"}";

        mvc.perform(post("/api/sources").contentType("application/json").content(sourceJson))
                .andExpect(status().isOk());

        mvc.perform(post("/api/sources/1/fetch"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/intake/candidates/1/promote"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleId").value(1));

        mvc.perform(post("/api/briefs/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.briefDate").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.markdownPath", containsString("daily-briefs")));

        mvc.perform(get("/api/briefs/" + LocalDate.now()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", containsString("美联储释放降息信号")));
    }

    @Test
    void intakeFetchCreatesReviewedCandidatesAndPromotesToArticle() throws Exception {
        String articleUrl = "http://localhost:" + server.getAddress().getPort() + "/article";
        String sourceJson = "{\"name\":\"宏观网页\",\"type\":\"WEB\",\"url\":\"" + articleUrl + "\",\"enabled\":true,"
                + "\"scheduledEnabled\":false,\"scheduleTimes\":\"08:30\",\"maxItemsPerRun\":1,"
                + "\"fetchFrequencyMinutes\":60,\"credibility\":4,\"tags\":\"宏观,市场\"}";

        mvc.perform(post("/api/sources").contentType("application/json").content(sourceJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxItemsPerRun").value(1))
                .andExpect(jsonPath("$.scheduleTimes").value("08:30"));

        mvc.perform(post("/api/sources/1/intake-fetch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.candidateCount").value(1))
                .andExpect(jsonPath("$.agentReviewedCount").value(1))
                .andExpect(jsonPath("$.batchSummaryText", containsString("本批共 1 条候选")));

        mvc.perform(get("/api/intake/candidates?status=PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].chineseTitle", containsString("美联储")))
                .andExpect(jsonPath("$[0].decisionSummary", containsString("值得")))
                .andExpect(jsonPath("$[0].agentStatus").value("FALLBACK"))
                .andExpect(jsonPath("$[0].humanStatus").value("PENDING"));

        mvc.perform(post("/api/intake/candidates/1/promote"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateId").value(1))
                .andExpect(jsonPath("$.articleId").value(1))
                .andExpect(jsonPath("$.status").value("PROMOTED"))
                .andExpect(jsonPath("$.workflowStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.eventId").value(1))
                .andExpect(jsonPath("$.eventTitle", containsString("美联储")))
                .andExpect(jsonPath("$.evidenceCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.learningTaskCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.contentIdeaCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.workflowSummary", containsString("研究工作包")));

        mvc.perform(get("/api/articles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", containsString("美联储")))
                .andExpect(jsonPath("$.insightCard.cardMarkdown", containsString("情报卡片")));

        mvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].canonicalTitle", containsString("美联储")))
                .andExpect(jsonPath("$[0].articleCount").value(1))
                .andExpect(jsonPath("$[0].evidenceCount").value(greaterThanOrEqualTo(1)));

        mvc.perform(get("/api/evidence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)));

        mvc.perform(get("/api/learning-tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)));

        mvc.perform(get("/api/content-ideas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)));

        mvc.perform(get("/api/intake/candidates?status=PROMOTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].promotedArticleId").value(1));
    }

    @Test
    void intakePromoteIsIdempotentAndReturnsExistingResearchWorkflow() throws Exception {
        String articleUrl = "http://localhost:" + server.getAddress().getPort() + "/article";
        String sourceJson = "{\"name\":\"宏观网页\",\"type\":\"WEB\",\"url\":\"" + articleUrl + "\",\"enabled\":true,"
                + "\"scheduledEnabled\":false,\"scheduleTimes\":\"08:30\",\"maxItemsPerRun\":1,"
                + "\"fetchFrequencyMinutes\":60,\"credibility\":4,\"tags\":\"宏观,市场\"}";

        mvc.perform(post("/api/sources").contentType("application/json").content(sourceJson))
                .andExpect(status().isOk());

        mvc.perform(post("/api/sources/1/intake-fetch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateCount").value(1));

        mvc.perform(post("/api/intake/candidates/1/promote"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleId").value(1))
                .andExpect(jsonPath("$.workflowStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.eventId").value(1));

        mvc.perform(post("/api/intake/candidates/1/promote"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleId").value(1))
                .andExpect(jsonPath("$.workflowStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.eventId").value(1))
                .andExpect(jsonPath("$.workflowSummary", containsString("研究工作包")));

        mvc.perform(get("/api/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].articleCount").value(1));
    }

    @Test
    void intakeFetchStoresSuccessfulAgentReviewStatusAndModel() throws Exception {
        when(llmChatClient.isConfigured()).thenReturn(true);
        when(llmChatClient.modelName()).thenReturn("fake-intake-model");
        when(llmChatClient.complete(anyString(), anyString())).thenReturn(candidateReviewJson());
        String articleUrl = "http://localhost:" + server.getAddress().getPort() + "/article";
        String sourceJson = "{\"name\":\"宏观网页\",\"type\":\"WEB\",\"url\":\"" + articleUrl + "\",\"enabled\":true,"
                + "\"scheduledEnabled\":false,\"scheduleTimes\":\"08:30\",\"maxItemsPerRun\":1,"
                + "\"fetchFrequencyMinutes\":60,\"credibility\":4,\"tags\":\"宏观,市场\"}";

        mvc.perform(post("/api/sources").contentType("application/json").content(sourceJson))
                .andExpect(status().isOk());

        mvc.perform(post("/api/sources/1/intake-fetch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.candidateCount").value(1));

        mvc.perform(get("/api/intake/candidates?status=PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].chineseTitle").value("美联储降息信号推动黄金走强"))
                .andExpect(jsonPath("$[0].decisionSummary", containsString("一针见血")))
                .andExpect(jsonPath("$[0].agentStatus").value("SUCCESS"))
                .andExpect(jsonPath("$[0].agentModel").value("fake-intake-model"));
    }

    @Test
    void intakeFetchWithNoCandidatesMarksBatchFailed() throws Exception {
        String emptyRssUrl = "http://localhost:" + server.getAddress().getPort() + "/empty-rss";
        String sourceJson = "{\"name\":\"空 RSS\",\"type\":\"RSS\",\"url\":\"" + emptyRssUrl + "\",\"enabled\":true,"
                + "\"scheduledEnabled\":false,\"scheduleTimes\":\"08:30\",\"maxItemsPerRun\":5,"
                + "\"fetchFrequencyMinutes\":60,\"credibility\":3,\"tags\":\"测试\"}";

        mvc.perform(post("/api/sources").contentType("application/json").content(sourceJson))
                .andExpect(status().isOk());

        mvc.perform(post("/api/sources/1/intake-fetch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.candidateCount").value(0))
                .andExpect(jsonPath("$.errorMessage", containsString("没有产出候选")));
    }

    @Test
    void vaultBriefMarkdownIsIndexedWhenDatabaseIsEmpty() throws Exception {
        Path dailyBriefs = Paths.get("target/test-data/api/vault/daily-briefs");
        Files.createDirectories(dailyBriefs);
        Files.write(dailyBriefs.resolve("2026-06-25.md"), (
                "# 每日金融、投资、创业学习简报 - 2026-06-25\n\n"
                        + "生成时间：2026-06-25 09:16 CST\n\n"
                        + "## 今日摘要\n\n"
                        + "市场正在从流动性和主题催化转向制度、融资窗口和资本效率。\n")
                .getBytes(StandardCharsets.UTF_8));

        mvc.perform(get("/api/briefs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].briefDate").value(hasItem("2026-06-25")))
                .andExpect(jsonPath("$[*].title").value(hasItem("每日金融、投资、创业学习简报 - 2026-06-25")))
                .andExpect(jsonPath("$[*].markdownPath").value(hasItem(containsString("vault/daily-briefs/2026-06-25.md"))));

        mvc.perform(get("/api/briefs/2026-06-25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", containsString("## 今日摘要")))
                .andExpect(jsonPath("$.content", containsString("资本效率")));
    }

    @Test
    void manualUrlCanBeIngestedAsInsightCardAndUsedByBrief() throws Exception {
        String articleUrl = "http://localhost:" + server.getAddress().getPort() + "/article";

        ingestUrlAndWait(articleUrl, "手动研究", "宏观,黄金");

        mvc.perform(get("/api/articles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("美联储暗示降息 黄金ETF获得资金流入"))
                .andExpect(jsonPath("$.sourceName").value("手动研究"))
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
                .andExpect(jsonPath("$[*].nodeName").value(hasItem("article-interpret")))
                .andExpect(jsonPath("$[*].status").value(hasItem("FALLBACK")))
                .andExpect(jsonPath("$[*].output").value(hasItem(containsString("FALLBACK"))));

        mvc.perform(post("/api/briefs/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", containsString("## 今日新变量")))
                .andExpect(jsonPath("$.content", containsString("## 今天要补的金融知识")));
    }

    @Test
    void manualUrlIngestReturnsTaskIdAndTaskEventuallyCompletes() throws Exception {
        String articleUrl = "http://localhost:" + server.getAddress().getPort() + "/article";

        MvcResult submitted = mvc.perform(post("/api/articles/ingest-url")
                        .contentType("application/json")
                        .content("{\"url\":\"" + articleUrl + "\",\"sourceName\":\"手动研究\",\"tags\":\"宏观,黄金\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn();

        String taskId = extractJsonString(submitted.getResponse().getContentAsString(), "taskId");
        String completed = waitForTask(taskId);

        assertTrue(completed.contains("\"status\":\"COMPLETED\""));
        assertTrue(completed.contains("\"phase\":\"COMPLETED\""));
        assertTrue(completed.contains("\"article\""));
        assertTrue(completed.contains("\"articleId\":1"));
    }

    @Test
    void manualUrlIngestExposesLlmPhaseWhileCardGenerationIsRunning() throws Exception {
        CountDownLatch llmStarted = new CountDownLatch(1);
        CountDownLatch releaseLlm = new CountDownLatch(1);
        when(llmChatClient.isConfigured()).thenReturn(true);
        when(llmChatClient.modelName()).thenReturn("fake-openai-model");
        when(llmChatClient.complete(anyString(), anyString())).thenAnswer(invocation -> {
            llmStarted.countDown();
            assertTrue(releaseLlm.await(3, TimeUnit.SECONDS));
            return cloudflareInterpretationJson();
        });
        String articleUrl = "http://localhost:" + server.getAddress().getPort() + "/cloudflare";

        String taskId = submitIngestTask(articleUrl, "技术观察", "技术工具");
        assertTrue(llmStarted.await(3, TimeUnit.SECONDS));

        try {
            mvc.perform(get("/api/tasks/" + taskId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("RUNNING"))
                    .andExpect(jsonPath("$.phase").value("LLM"))
                    .andExpect(jsonPath("$.message").value("正在生成情报卡片"));
        } finally {
            releaseLlm.countDown();
        }
        String completed = waitForTask(taskId);
        assertTrue(completed.contains("\"status\":\"COMPLETED\""));
    }

    @Test
    void manualUrlIngestOnlyCapturesEvidenceForFinanceAndMarketCategories() throws Exception {
        String selfImprovementUrl = "http://localhost:" + server.getAddress().getPort() + "/article";
        String financeUrl = "http://localhost:" + server.getAddress().getPort() + "/fed-followup";

        ingestUrlAndWaitWithCategory(selfImprovementUrl, "手动研究", "自我提升");

        mvc.perform(get("/api/evidence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        ingestUrlAndWaitWithCategory(financeUrl, "CNBC", "金融");

        mvc.perform(get("/api/evidence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void eventMemoryGroupsRelatedArticles() throws Exception {
        String firstUrl = "http://localhost:" + server.getAddress().getPort() + "/article";
        String followUpUrl = "http://localhost:" + server.getAddress().getPort() + "/fed-followup";

        ingestUrlAndWait(firstUrl, "Reuters", "宏观,市场");
        ingestUrlAndWait(followUpUrl, "CNBC", "宏观,市场");

        mvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].canonicalTitle", containsString("美联储")))
                .andExpect(jsonPath("$[0].themeCode").value("china_macro"))
                .andExpect(jsonPath("$[0].articleCount").value(2))
                .andExpect(jsonPath("$[0].noveltyState").value("FOLLOW_UP"));

        mvc.perform(get("/api/events/1/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].noveltyType").value(hasItem("NEW")))
                .andExpect(jsonPath("$[*].noveltyType").value(hasItem("FOLLOW_UP")));
    }

    @Test
    void eventGovernanceUpdatesStatusAndRejectsInvalidStatus() throws Exception {
        String firstUrl = "http://localhost:" + server.getAddress().getPort() + "/article";
        ingestUrlAndWait(firstUrl, "Reuters", "宏观,市场");

        mvc.perform(post("/api/events/1/status")
                        .contentType("application/json")
                        .content("{\"status\":\"COOLING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COOLING"));

        mvc.perform(post("/api/events/1/status")
                        .contentType("application/json")
                        .content("{\"status\":\"BROKEN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Unsupported event status: BROKEN")));
    }

    @Test
    void archivedEventDoesNotReceiveNewAutoIngestedArticles() throws Exception {
        String firstUrl = "http://localhost:" + server.getAddress().getPort() + "/article";
        String followUpUrl = "http://localhost:" + server.getAddress().getPort() + "/fed-followup";

        ingestUrlAndWait(firstUrl, "Reuters", "宏观,市场");
        mvc.perform(post("/api/events/1/status")
                        .contentType("application/json")
                        .content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        ingestUrlAndWait(followUpUrl, "CNBC", "宏观,市场");

        mvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/events/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.articleCount").value(1));
        mvc.perform(get("/api/events/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleCount").value(1));
    }

    @Test
    void eventGovernanceMergesEventsAndArchivesSource() throws Exception {
        String marketUrl = "http://localhost:" + server.getAddress().getPort() + "/article";
        String policyUrl = "http://localhost:" + server.getAddress().getPort() + "/pboc-policy";

        ingestUrlAndWait(marketUrl, "Reuters", "宏观,市场");
        ingestUrlAndWait(policyUrl, "中国人民银行", "宏观,政策");

        mvc.perform(post("/api/events/2/merge")
                        .contentType("application/json")
                        .content("{\"targetEventId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.articleCount").value(2));

        mvc.perform(get("/api/events/1/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/events/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.articleCount").value(0));
    }

    @Test
    void eventGovernanceRejectsMergeIntoArchivedTarget() throws Exception {
        String marketUrl = "http://localhost:" + server.getAddress().getPort() + "/article";
        String policyUrl = "http://localhost:" + server.getAddress().getPort() + "/pboc-policy";

        ingestUrlAndWait(marketUrl, "Reuters", "宏观,市场");
        ingestUrlAndWait(policyUrl, "中国人民银行", "宏观,政策");
        mvc.perform(post("/api/events/1/status")
                        .contentType("application/json")
                        .content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/events/2/merge")
                        .contentType("application/json")
                        .content("{\"targetEventId\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("Cannot govern archived target event")));
    }

    @Test
    void eventGovernanceMovesArticleIntoNewEvent() throws Exception {
        String firstUrl = "http://localhost:" + server.getAddress().getPort() + "/article";
        String followUpUrl = "http://localhost:" + server.getAddress().getPort() + "/fed-followup";

        ingestUrlAndWait(firstUrl, "Reuters", "宏观,市场");
        ingestUrlAndWait(followUpUrl, "CNBC", "宏观,市场");

        mvc.perform(post("/api/events/1/articles/2/move")
                        .contentType("application/json")
                        .content("{\"createNewEvent\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.articleCount").value(1));

        mvc.perform(get("/api/events/1/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/events/2/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].noveltyReason", containsString("人工治理调整")));
        mvc.perform(get("/api/events/2/evidence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void eventListSupportsResearchFilters() throws Exception {
        String marketUrl = "http://localhost:" + server.getAddress().getPort() + "/article";
        String policyUrl = "http://localhost:" + server.getAddress().getPort() + "/pboc-policy";

        ingestUrlAndWait(marketUrl, "Reuters", "宏观,市场");
        ingestUrlAndWait(policyUrl, "中国人民银行", "宏观,政策");

        mvc.perform(get("/api/events")
                        .param("themeCode", "china_macro")
                        .param("noveltyState", "NEW")
                        .param("dateFrom", LocalDate.now().toString())
                        .param("dateTo", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].themeCode").value(hasItem("china_macro")))
                .andExpect(jsonPath("$[*].noveltyState").value(hasItem("NEW")));
    }

    @Test
    void eventEvidenceReturnsStructuredClaims() throws Exception {
        String firstUrl = "http://localhost:" + server.getAddress().getPort() + "/article";
        String followUpUrl = "http://localhost:" + server.getAddress().getPort() + "/fed-followup";

        ingestUrlAndWait(firstUrl, "Reuters", "宏观,市场");
        ingestUrlAndWait(followUpUrl, "CNBC", "宏观,市场");

        mvc.perform(get("/api/events/1/evidence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].sourceTier").value(hasItem("MEDIA")))
                .andExpect(jsonPath("$[*].evidenceType").value(hasItem("DATA")))
                .andExpect(jsonPath("$[*].claim").value(hasItem(containsString("12亿"))))
                .andExpect(jsonPath("$[*].confidence").value(hasItem(greaterThanOrEqualTo(70))));
    }

    @Test
    void evidenceLedgerListsCapturedEvidenceAcrossEvents() throws Exception {
        String firstUrl = "http://localhost:" + server.getAddress().getPort() + "/article";
        String policyUrl = "http://localhost:" + server.getAddress().getPort() + "/pboc-policy";

        ingestUrlAndWait(firstUrl, "Reuters", "宏观,市场");
        ingestUrlAndWait(policyUrl, "中国人民银行", "宏观,政策");

        mvc.perform(get("/api/evidence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$[*].sourceTier").value(hasItem("MEDIA")))
                .andExpect(jsonPath("$[*].sourceTier").value(hasItem("REGULATOR")))
                .andExpect(jsonPath("$[*].claim").value(hasItem(containsString("MLF"))));
    }

    @Test
    void evidenceLedgerSupportsDetailAndFiltering() throws Exception {
        String firstUrl = "http://localhost:" + server.getAddress().getPort() + "/article";
        String policyUrl = "http://localhost:" + server.getAddress().getPort() + "/pboc-policy";

        ingestUrlAndWait(firstUrl, "Reuters", "宏观,市场");
        ingestUrlAndWait(policyUrl, "中国人民银行", "宏观,政策");

        mvc.perform(get("/api/evidence")
                        .param("eventId", "2")
                        .param("sourceTier", "REGULATOR")
                        .param("evidenceType", "TIMELINE")
                        .param("minConfidence", "80"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].eventId").value(2))
                .andExpect(jsonPath("$[0].sourceTier").value("REGULATOR"))
                .andExpect(jsonPath("$[0].evidenceType").value("TIMELINE"))
                .andExpect(jsonPath("$[0].confidence").value(greaterThanOrEqualTo(80)));

        mvc.perform(get("/api/evidence/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(2))
                .andExpect(jsonPath("$.claim", containsString("MLF")));
    }

    @Test
    void learningTasksAndContentIdeasAreGeneratedFromEvents() throws Exception {
        String firstUrl = "http://localhost:" + server.getAddress().getPort() + "/article";
        String followUpUrl = "http://localhost:" + server.getAddress().getPort() + "/fed-followup";

        ingestUrlAndWait(firstUrl, "Reuters", "宏观,市场");
        ingestUrlAndWait(followUpUrl, "CNBC", "宏观,市场");

        mvc.perform(get("/api/learning-tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$[0].eventId").value(1))
                .andExpect(jsonPath("$[0].themeCode").value("china_macro"))
                .andExpect(jsonPath("$[0].question").isNotEmpty())
                .andExpect(jsonPath("$[0].status").value("TODO"));

        mvc.perform(get("/api/content-ideas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$[0].eventId").value(1))
                .andExpect(jsonPath("$[0].title").isNotEmpty())
                .andExpect(jsonPath("$[0].angle").isNotEmpty())
                .andExpect(jsonPath("$[0].format").isNotEmpty())
                .andExpect(jsonPath("$[0].score").value(greaterThanOrEqualTo(60)))
                .andExpect(jsonPath("$[0].outline", containsString("1.")));
    }

    @Test
    void generatedResearchArtifactsUseEventContext() throws Exception {
        String marketReactionUrl = "http://localhost:" + server.getAddress().getPort() + "/article";
        String policyUrl = "http://localhost:" + server.getAddress().getPort() + "/pboc-policy";

        ingestUrlAndWait(marketReactionUrl, "Reuters", "宏观,市场");
        ingestUrlAndWait(policyUrl, "中国人民银行", "宏观,政策");

        mvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mvc.perform(get("/api/learning-tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].concepts").value(hasItem(containsString("黄金"))))
                .andExpect(jsonPath("$[*].concepts").value(hasItem(containsString("MLF"))))
                .andExpect(jsonPath("$[*].whyNeeded").value(hasItem(containsString("政策工具"))));

        mvc.perform(get("/api/content-ideas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].angle").value(hasItem(containsString("实际利率"))))
                .andExpect(jsonPath("$[*].angle").value(hasItem(containsString("政策工具"))))
                .andExpect(jsonPath("$[*].scoreReason").value(hasItem(containsString("政策信号"))));
    }

    @Test
    void learningTaskStatusUpdateIsValidated() throws Exception {
        seedResearchArtifacts();

        mvc.perform(post("/api/learning-tasks/1/status")
                        .contentType("application/json")
                        .content("{\"status\":\"LEARNING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("LEARNING"));

        mvc.perform(post("/api/learning-tasks/1/status")
                        .contentType("application/json")
                        .content("{\"status\":\"broken\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Unsupported learning task status: broken")));

        mvc.perform(post("/api/learning-tasks/999/status")
                        .contentType("application/json")
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", containsString("Learning task not found: 999")));
    }

    @Test
    void contentIdeaStatusAndDetailAreValidated() throws Exception {
        seedResearchArtifacts();

        mvc.perform(get("/api/content-ideas/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value(not(containsString("DRAFTING"))));

        mvc.perform(post("/api/content-ideas/1/status")
                        .contentType("application/json")
                        .content("{\"status\":\"DRAFTING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("DRAFTING"));

        mvc.perform(post("/api/content-ideas/1/status")
                        .contentType("application/json")
                        .content("{\"status\":\"INVALID\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Unsupported content idea status: INVALID")));

        mvc.perform(get("/api/content-ideas/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", containsString("Content idea not found: 999")));
    }

    @Test
    void briefIncludesResearchSectionsAndExposesResearchContext() throws Exception {
        String firstUrl = "http://localhost:" + server.getAddress().getPort() + "/article";
        String followUpUrl = "http://localhost:" + server.getAddress().getPort() + "/fed-followup";

        ingestUrlAndWait(firstUrl, "Reuters", "宏观,市场");
        ingestUrlAndWait(followUpUrl, "CNBC", "宏观,市场");

        mvc.perform(post("/api/briefs/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("每日金融、投资、创业学习简报 - " + LocalDate.now()))
                .andExpect(jsonPath("$.content", containsString("## 今日新变量")))
                .andExpect(jsonPath("$.content", containsString("## 事件追踪")))
                .andExpect(jsonPath("$.content", containsString("## 中国宏观")))
                .andExpect(jsonPath("$.content", containsString("## 今日证据来源")))
                .andExpect(jsonPath("$.content", containsString("## 今天要补的金融知识")))
                .andExpect(jsonPath("$.content", containsString("## 可发展为自媒体选题")))
                .andExpect(jsonPath("$.content", containsString("## 今日思考题")));

        mvc.perform(get("/api/briefs/" + LocalDate.now() + "/research-context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.briefDate").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.events.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.evidenceItems.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.learningTasks.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.contentIdeas.length()").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void researchRunExecutesEndToEndFromThemes() throws Exception {
        String macroSource = "{\"name\":\"Macro Source\",\"type\":\"RSS\",\"url\":\"" + rssUrl + "\",\"enabled\":true,"
                + "\"fetchFrequencyMinutes\":60,\"credibility\":5,\"tags\":\"china_macro,macro\"}";

        mvc.perform(post("/api/sources").contentType("application/json").content(macroSource))
                .andExpect(status().isOk());

        mvc.perform(post("/api/research/runs")
                        .contentType("application/json")
                        .content("{\"runDate\":\"" + LocalDate.now() + "\",\"themeCodes\":[\"china_macro\"],"
                                + "\"maxSourcesPerTheme\":1,\"includeDisabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.themeCodes.length()").value(1))
                .andExpect(jsonPath("$.sourceCount").value(1))
                .andExpect(jsonPath("$.fetchedSourceCount").value(0))
                .andExpect(jsonPath("$.plannedSources.length()").value(1))
                .andExpect(jsonPath("$.plannedSources[*].sourceName").value(hasItem("Macro Source")))
                .andExpect(jsonPath("$.summary", containsString("Planned 1 sources")));

        String completed = waitForResearchRun(1L);
        assertTrue(completed.contains("\"status\":\"COMPLETED\""));

        mvc.perform(get("/api/research/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].sourceCount").value(1))
                .andExpect(jsonPath("$[0].eventCount").value(greaterThanOrEqualTo(1)));

        mvc.perform(get("/api/research/runs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.status").value("COMPLETED"))
                .andExpect(jsonPath("$.plannedSources.length()").value(1))
                .andExpect(jsonPath("$.plannedSources[*].sourceName").value(hasItem("Macro Source")))
                .andExpect(jsonPath("$.planSteps.length()").value(6))
                .andExpect(jsonPath("$.planSteps[*].stepId").value(hasItem("plan_sources")))
                .andExpect(jsonPath("$.planSteps[*].stepId").value(hasItem("fetch_sources")))
                .andExpect(jsonPath("$.planSteps[*].status").value(hasItem("COMPLETED")))
                .andExpect(jsonPath("$.agentRuns.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.agentRuns[*].nodeName").value(hasItem("research-orchestrate")))
                .andExpect(jsonPath("$.agentRuns[*].nodeName").value(hasItem("article-interpret")))
                .andExpect(jsonPath("$.agentRuns[*].nodeName").value(hasItem("evidence-extract")))
                .andExpect(jsonPath("$.agentRuns[*].nodeName").value(hasItem("learning-generate")))
                .andExpect(jsonPath("$.agentRuns[*].nodeName").value(hasItem("content-idea-generate")))
                .andExpect(jsonPath("$.agentRuns[*].nodeName").value(hasItem("brief-synthesize")))
                .andExpect(jsonPath("$.agentRuns[*].researchRunId").value(hasItem(1)));

        mvc.perform(get("/api/briefs/" + LocalDate.now()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", containsString("## 今日新变量")))
                .andExpect(jsonPath("$.content", containsString("美联储释放降息信号")));
    }

    @Test
    void deletingArticleCleansResearchArtifactsAndCounts() throws Exception {
        seedResearchArtifacts();

        mvc.perform(get("/api/events/1/evidence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mvc.perform(delete("/api/articles/batch")
                        .contentType("application/json")
                        .content("{\"ids\":[1]}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/events/1/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/events/1/evidence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/events/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleCount").value(1))
                .andExpect(jsonPath("$.evidenceCount").value(1));
    }

    @Test
    void configuredAgentInterpretsArticleCardsTopicsAndTrace() throws Exception {
        when(llmChatClient.isConfigured()).thenReturn(true);
        when(llmChatClient.modelName()).thenReturn("fake-openai-model");
        when(llmChatClient.complete(anyString(), anyString())).thenReturn(cloudflareInterpretationJson());
        String articleUrl = "http://localhost:" + server.getAddress().getPort() + "/cloudflare";

        ingestUrlAndWait(articleUrl, "技术观察", "技术工具");

        mvc.perform(get("/api/articles/1"))
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

        String taskId = submitIngestTask(shellUrl, "手动研究", "市场");
        String failed = waitForTask(taskId);

        assertTrue(failed.contains("\"status\":\"FAILED\""));
        assertTrue(failed.contains("\"errorMessage\""));
        assertTrue(failed.contains("URL:"));
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
        mvc.perform(post("/api/intake/candidates/1/promote"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleId").value(1));
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

    @Test
    void topicCanBeDeletedWithoutDeletingLinkedContent() throws Exception {
        String sourceJson = "{\"name\":\"测试财经RSS\",\"type\":\"RSS\",\"url\":\"" + rssUrl + "\",\"enabled\":true,"
                + "\"fetchFrequencyMinutes\":60,\"credibility\":4,\"tags\":\"宏观,市场\"}";

        mvc.perform(post("/api/sources").contentType("application/json").content(sourceJson))
                .andExpect(status().isOk());
        mvc.perform(post("/api/sources/1/fetch"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/intake/candidates/1/promote"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleId").value(1));
        mvc.perform(post("/api/briefs/generate"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/topics/from-article/1"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/topics/from-brief/" + LocalDate.now()))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/topics/1"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/articles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("美联储释放降息信号 黄金走强"));
        mvc.perform(get("/api/briefs/" + LocalDate.now()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", containsString("每日")));
    }

    private void deleteIfExists(String table) {
        try {
            jdbcTemplate.update("DELETE FROM " + table);
        } catch (Exception ignored) {
            // Older schemas do not have V3 link tables before the initializer runs.
        }
    }

    private void seedResearchArtifacts() throws Exception {
        String firstUrl = "http://localhost:" + server.getAddress().getPort() + "/article";
        String followUpUrl = "http://localhost:" + server.getAddress().getPort() + "/fed-followup";

        ingestUrlAndWait(firstUrl, "Reuters", "宏观,市场");
        ingestUrlAndWait(followUpUrl, "CNBC", "宏观,市场");
    }

    private void ingestUrlAndWait(String url, String sourceName, String tags) throws Exception {
        String taskId = submitIngestTask(url, sourceName, tags);
        String completed = waitForTask(taskId);
        assertTrue(completed.contains("\"status\":\"COMPLETED\""));
    }

    private void ingestUrlAndWaitWithCategory(String url, String sourceName, String category) throws Exception {
        String taskId = submitIngestTaskWithCategory(url, sourceName, category);
        String completed = waitForTask(taskId);
        assertTrue(completed.contains("\"status\":\"COMPLETED\""));
    }

    private String submitIngestTask(String url, String sourceName, String tags) throws Exception {
        MvcResult submitted = mvc.perform(post("/api/articles/ingest-url")
                        .contentType("application/json")
                        .content("{\"url\":\"" + url + "\",\"sourceName\":\"" + sourceName + "\",\"tags\":\"" + tags + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").isNotEmpty())
                .andReturn();
        return extractJsonString(submitted.getResponse().getContentAsString(), "taskId");
    }

    private String submitIngestTaskWithCategory(String url, String sourceName, String category) throws Exception {
        MvcResult submitted = mvc.perform(post("/api/articles/ingest-url")
                        .contentType("application/json")
                        .content("{\"url\":\"" + url + "\",\"sourceName\":\"" + sourceName
                                + "\",\"category\":\"" + category + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").isNotEmpty())
                .andReturn();
        return extractJsonString(submitted.getResponse().getContentAsString(), "taskId");
    }

    private String waitForTask(String taskId) throws Exception {
        for (int attempt = 0; attempt < 30; attempt++) {
            MvcResult result = mvc.perform(get("/api/tasks/" + taskId))
                    .andExpect(status().isOk())
                    .andReturn();
            String body = result.getResponse().getContentAsString();
            if (body.contains("\"status\":\"COMPLETED\"") || body.contains("\"status\":\"FAILED\"")) {
                return body;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw ex;
            }
        }
        throw new AssertionError("Task did not finish in time: " + taskId);
    }

    private String waitForResearchRun(Long runId) throws Exception {
        for (int attempt = 0; attempt < 60; attempt++) {
            MvcResult result = mvc.perform(get("/api/research/runs"))
                    .andExpect(status().isOk())
                    .andReturn();
            String body = result.getResponse().getContentAsString();
            if (body.contains("\"status\":\"COMPLETED\"") || body.contains("\"status\":\"FAILED\"")) {
                return body;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw ex;
            }
        }
        throw new AssertionError("Research run did not finish in time: " + runId);
    }

    private String extractJsonString(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            throw new AssertionError("Missing JSON field " + field + " in " + json);
        }
        int valueStart = start + marker.length();
        int valueEnd = json.indexOf("\"", valueStart);
        if (valueEnd < 0) {
            throw new AssertionError("Unterminated JSON field " + field + " in " + json);
        }
        return json.substring(valueStart, valueEnd);
    }

    private String rss() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rss version=\"2.0\"><channel><title>Test Feed</title>"
                + "<item>"
                + "<title>美联储释放降息信号 黄金走强</title>"
                + "<link>https://example.com/fed-gold</link>"
                + "<description>宏观市场关注美联储降息预期，黄金价格继续走强。</description>"
                + "<pubDate>" + DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC)) + "</pubDate>"
                + "</item>"
                + "</channel></rss>";
    }

    private String emptyRss() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rss version=\"2.0\"><channel><title>Empty Feed</title></channel></rss>";
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

    private String fedFollowUpArticle() {
        return "<!doctype html><html><head>"
                + "<title>美联储降息预期升温 黄金ETF单周流入12亿美元</title>"
                + "<meta name=\"description\" content=\"美联储降息预期继续升温，黄金ETF单周资金流入达到12亿美元。\">"
                + "</head><body><article>"
                + "<h1>美联储降息预期升温 黄金ETF单周流入12亿美元</h1>"
                + "<p>美联储降息预期继续升温，黄金ETF单周资金流入达到12亿美元。</p>"
                + "<p>分析师认为实际利率下行预期是黄金走强的重要变量。</p>"
                + "</article></body></html>";
    }

    private String pbocPolicyArticle() {
        return "<!doctype html><html><head>"
                + "<title>央行开展3000亿元MLF操作并下调利率10个基点</title>"
                + "<meta name=\"description\" content=\"中国人民银行开展3000亿元MLF操作，并将中期借贷便利利率下调10个基点。\">"
                + "</head><body><article>"
                + "<h1>央行开展3000亿元MLF操作并下调利率10个基点</h1>"
                + "<p>中国人民银行公告称，今日开展3000亿元MLF操作，并将中期借贷便利利率下调10个基点。</p>"
                + "<p>机构认为，这一政策信号将影响银行负债成本、信用扩张节奏以及后续LPR报价。</p>"
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

    private String candidateReviewJson() {
        return "{"
                + "\"chineseTitle\":\"美联储降息信号推动黄金走强\","
                + "\"decisionSummary\":\"一针见血：这条信息说明市场正在重新定价美联储降息预期和黄金资产。\","
                + "\"keyFacts\":[\"美联储释放降息信号\",\"黄金价格继续走强\"],"
                + "\"whyItMatters\":\"它会影响利率预期、黄金和风险资产的短期定价。\","
                + "\"noveltyJudgment\":\"NEW_EVENT\","
                + "\"riskFlags\":[],"
                + "\"score\":88,"
                + "\"recommendation\":\"PROMOTABLE\","
                + "\"reason\":\"具备明确宏观变量和资产价格影响，适合人工判断是否入库。\""
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
