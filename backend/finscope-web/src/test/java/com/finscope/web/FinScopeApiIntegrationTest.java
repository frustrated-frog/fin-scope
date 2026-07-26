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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.beans.factory.annotation.Value;

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
import java.util.UUID;
import java.util.zip.ZipFile;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
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

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FinScopeApiIntegrationTest {
    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        String testId = UUID.randomUUID().toString();
        registry.add("finscope.data-root", () -> "target/test-data/api-" + testId);
        registry.add("spring.datasource.url",
                () -> "jdbc:sqlite:target/test-data/api-" + testId + "/finance.db");
    }

    @Resource
    private MockMvc mvc;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Value("${finscope.data-root}")
    private String dataRoot;

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
        deleteIfExists("research_run_output");
        deleteIfExists("research_report");
        deleteIfExists("research_evaluation_metric");
        deleteIfExists("research_evaluation");
        deleteIfExists("research_runtime_event");
        deleteIfExists("research_runtime_checkpoint");
        deleteIfExists("research_mission_gap");
        deleteIfExists("research_mission_task");
        deleteIfExists("research_mission");
        deleteIfExists("thesis_finding");
        deleteIfExists("research_run_plan");
        deleteIfExists("research_run");
        deleteIfExists("research_thesis");
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
                + "'research_run_output','research_report','research_mission_task','research_mission_gap',"
                + "'thesis_finding','research_thesis',"
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
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.name").value("测试财经RSS"));

        mvc.perform(post("/api/sources/1/fetch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.candidateCount").value(1));

        mvc.perform(get("/api/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mvc.perform(get("/api/intake/candidates?status=PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].originalTitle").value("美联储释放降息信号 黄金走强"));

        mvc.perform(post("/api/sources/1/fetch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.duplicateCount").value(greaterThanOrEqualTo(1)));

        mvc.perform(get("/api/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
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
                .andExpect(jsonPath("$.data.articleId").value(1));

        mvc.perform(post("/api/briefs/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.briefDate").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.data.markdownPath", containsString("daily-briefs")));

        mvc.perform(get("/api/briefs/" + LocalDate.now()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", containsString("美联储释放降息信号")));
    }

    @Test
    void intakeFetchCreatesReviewedCandidatesAndPromotesToArticle() throws Exception {
        String articleUrl = "http://localhost:" + server.getAddress().getPort() + "/article";
        String sourceJson = "{\"name\":\"宏观网页\",\"type\":\"WEB\",\"url\":\"" + articleUrl + "\",\"enabled\":true,"
                + "\"scheduledEnabled\":false,\"scheduleTimes\":\"08:30\",\"maxItemsPerRun\":1,"
                + "\"fetchFrequencyMinutes\":60,\"credibility\":4,\"tags\":\"宏观,市场\"}";

        mvc.perform(post("/api/sources").contentType("application/json").content(sourceJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maxItemsPerRun").value(1))
                .andExpect(jsonPath("$.data.scheduleTimes").value("08:30"));

        mvc.perform(post("/api/sources/1/intake-fetch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.candidateCount").value(1))
                .andExpect(jsonPath("$.data.agentReviewedCount").value(1))
                .andExpect(jsonPath("$.data.batchSummaryText", containsString("本批共 1 条候选")));

        mvc.perform(get("/api/intake/candidates?status=PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].chineseTitle", containsString("美联储")))
                .andExpect(jsonPath("$.data[0].decisionSummary", containsString("值得")))
                .andExpect(jsonPath("$.data[0].agentStatus").value("FALLBACK"))
                .andExpect(jsonPath("$.data[0].humanStatus").value("PENDING"));

        mvc.perform(post("/api/intake/candidates/1/promote"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidateId").value(1))
                .andExpect(jsonPath("$.data.articleId").value(1))
                .andExpect(jsonPath("$.data.status").value("PROMOTED"))
                .andExpect(jsonPath("$.data.workflowStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.eventId").value(1))
                .andExpect(jsonPath("$.data.eventTitle", containsString("美联储")))
                .andExpect(jsonPath("$.data.evidenceCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.learningTaskCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.contentIdeaCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.workflowSummary", containsString("研究工作包")));

        mvc.perform(get("/api/articles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title", containsString("美联储")))
                .andExpect(jsonPath("$.data.insightCard.cardMarkdown", containsString("情报卡片")));

        mvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].canonicalTitle", containsString("美联储")))
                .andExpect(jsonPath("$.data[0].articleCount").value(1))
                .andExpect(jsonPath("$.data[0].evidenceCount").value(greaterThanOrEqualTo(1)));

        mvc.perform(get("/api/evidence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)));

        mvc.perform(get("/api/learning-tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)));

        mvc.perform(get("/api/content-ideas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)));

        mvc.perform(get("/api/intake/candidates?status=PROMOTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].promotedArticleId").value(1));
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
                .andExpect(jsonPath("$.data.candidateCount").value(1));

        mvc.perform(post("/api/intake/candidates/1/promote"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.articleId").value(1))
                .andExpect(jsonPath("$.data.workflowStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.eventId").value(1));

        mvc.perform(post("/api/intake/candidates/1/promote"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.articleId").value(1))
                .andExpect(jsonPath("$.data.workflowStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.eventId").value(1))
                .andExpect(jsonPath("$.data.workflowSummary", containsString("研究工作包")));

        mvc.perform(get("/api/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].articleCount").value(1));
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
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.candidateCount").value(1));

        mvc.perform(get("/api/intake/candidates?status=PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].chineseTitle").value("美联储降息信号推动黄金走强"))
                .andExpect(jsonPath("$.data[0].decisionSummary", containsString("一针见血")))
                .andExpect(jsonPath("$.data[0].agentStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].agentModel").value("fake-intake-model"));
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
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.candidateCount").value(0))
                .andExpect(jsonPath("$.data.errorMessage", containsString("没有产出候选")));
    }

    @Test
    void vaultBriefMarkdownIsIndexedWhenDatabaseIsEmpty() throws Exception {
        Path dailyBriefs = Paths.get(dataRoot, "vault/daily-briefs");
        Files.createDirectories(dailyBriefs);
        Files.write(dailyBriefs.resolve("2026-06-25.md"), (
                "# 每日金融、投资、创业学习简报 - 2026-06-25\n\n"
                        + "生成时间：2026-06-25 09:16 CST\n\n"
                        + "## 今日摘要\n\n"
                        + "市场正在从流动性和主题催化转向制度、融资窗口和资本效率。\n")
                .getBytes(StandardCharsets.UTF_8));

        mvc.perform(get("/api/briefs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].briefDate").value(hasItem("2026-06-25")))
                .andExpect(jsonPath("$.data[*].title").value(hasItem("每日金融、投资、创业学习简报 - 2026-06-25")))
                .andExpect(jsonPath("$.data[*].markdownPath").value(hasItem(containsString("vault/daily-briefs/2026-06-25.md"))));

        mvc.perform(get("/api/briefs/2026-06-25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", containsString("## 今日摘要")))
                .andExpect(jsonPath("$.data.content", containsString("资本效率")));
    }

    @Test
    void manualUrlCanBeIngestedAsInsightCardAndUsedByBrief() throws Exception {
        String articleUrl = "http://localhost:" + server.getAddress().getPort() + "/article";

        ingestUrlAndWait(articleUrl, "手动研究", "宏观,黄金");

        mvc.perform(get("/api/articles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("美联储暗示降息 黄金ETF获得资金流入"))
                .andExpect(jsonPath("$.data.sourceName").value("手动研究"))
                .andExpect(jsonPath("$.data.insightCard.oneSentenceSummary", containsString("美联储")))
                .andExpect(jsonPath("$.data.insightCard.coreEvent", containsString("降息")))
                .andExpect(jsonPath("$.data.insightCard.importance", containsString("利率预期")))
                .andExpect(jsonPath("$.data.insightCard.impactTargets", containsString("黄金")))
                .andExpect(jsonPath("$.data.insightCard.cardMarkdown", containsString("### 后续观察")))
                .andExpect(jsonPath("$.data.insightCard.cardMarkdown", containsString("### 可沉淀主题")))
                .andExpect(jsonPath("$.data.insightCard.cardMarkdown", containsString("解读来源：FALLBACK")));

        mvc.perform(get("/api/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].insightCard.coreEvent", containsString("降息")));

        mvc.perform(get("/api/agent-runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].nodeName").value(hasItem("article-interpret")))
                .andExpect(jsonPath("$.data[*].status").value(hasItem("FALLBACK")))
                .andExpect(jsonPath("$.data[*].output").value(hasItem(containsString("FALLBACK"))));

        mvc.perform(post("/api/briefs/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", containsString("## 今日新变量")))
                .andExpect(jsonPath("$.data.content", containsString("## 今天要补的金融知识")));
    }

    @Test
    void manualUrlIngestReturnsTaskIdAndTaskEventuallyCompletes() throws Exception {
        String articleUrl = "http://localhost:" + server.getAddress().getPort() + "/article";

        MvcResult submitted = mvc.perform(post("/api/articles/ingest-url")
                        .contentType("application/json")
                        .content("{\"url\":\"" + articleUrl + "\",\"sourceName\":\"手动研究\",\"tags\":\"宏观,黄金\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
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
                    .andExpect(jsonPath("$.data.status").value("RUNNING"))
                    .andExpect(jsonPath("$.data.phase").value("LLM"))
                    .andExpect(jsonPath("$.data.message").value("正在生成情报卡片"));
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
                .andExpect(jsonPath("$.data.length()").value(0));

        ingestUrlAndWaitWithCategory(financeUrl, "CNBC", "金融");

        mvc.perform(get("/api/evidence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void eventMemoryGroupsRelatedArticles() throws Exception {
        String firstUrl = "http://localhost:" + server.getAddress().getPort() + "/article";
        String followUpUrl = "http://localhost:" + server.getAddress().getPort() + "/fed-followup";

        ingestUrlAndWait(firstUrl, "Reuters", "宏观,市场");
        ingestUrlAndWait(followUpUrl, "CNBC", "宏观,市场");

        mvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].canonicalTitle", containsString("美联储")))
                .andExpect(jsonPath("$.data[0].themeCode").value("china_macro"))
                .andExpect(jsonPath("$.data[0].articleCount").value(2))
                .andExpect(jsonPath("$.data[0].noveltyState").value("FOLLOW_UP"));

        mvc.perform(get("/api/events/1/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[*].noveltyType").value(hasItem("NEW")))
                .andExpect(jsonPath("$.data[*].noveltyType").value(hasItem("FOLLOW_UP")));
    }

    @Test
    void eventGovernanceUpdatesStatusAndRejectsInvalidStatus() throws Exception {
        String firstUrl = "http://localhost:" + server.getAddress().getPort() + "/article";
        ingestUrlAndWait(firstUrl, "Reuters", "宏观,市场");

        mvc.perform(post("/api/events/1/status")
                        .contentType("application/json")
                        .content("{\"status\":\"COOLING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COOLING"));

        mvc.perform(post("/api/events/1/status")
                        .contentType("application/json")
                        .content("{\"status\":\"BROKEN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FS-1002"))
                .andExpect(jsonPath("$.message", containsString("不支持的事件状态：BROKEN")));
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
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        ingestUrlAndWait(followUpUrl, "CNBC", "宏观,市场");

        mvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(get("/api/events/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.data.articleCount").value(1));
        mvc.perform(get("/api/events/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.articleCount").value(1));
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
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.articleCount").value(2));

        mvc.perform(get("/api/events/1/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(get("/api/events/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.data.articleCount").value(0));
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
                .andExpect(jsonPath("$.code").value("FS-2002"))
                .andExpect(jsonPath("$.message", containsString("已归档事件不能执行治理操作")))
                .andExpect(jsonPath("$.message", containsString("目标事件")));
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
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.articleCount").value(1));

        mvc.perform(get("/api/events/1/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/api/events/2/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].noveltyReason", containsString("人工治理调整")));
        mvc.perform(get("/api/events/2/evidence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
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
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[*].themeCode").value(hasItem("china_macro")))
                .andExpect(jsonPath("$.data[*].noveltyState").value(hasItem("NEW")));
    }

    @Test
    void eventEvidenceReturnsStructuredClaims() throws Exception {
        String firstUrl = "http://localhost:" + server.getAddress().getPort() + "/article";
        String followUpUrl = "http://localhost:" + server.getAddress().getPort() + "/fed-followup";

        ingestUrlAndWait(firstUrl, "Reuters", "宏观,市场");
        ingestUrlAndWait(followUpUrl, "CNBC", "宏观,市场");

        mvc.perform(get("/api/events/1/evidence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[*].sourceTier").value(hasItem("MEDIA")))
                .andExpect(jsonPath("$.data[*].evidenceType").value(hasItem("DATA")))
                .andExpect(jsonPath("$.data[*].claim").value(hasItem(containsString("12亿"))))
                .andExpect(jsonPath("$.data[*].confidence").value(hasItem(greaterThanOrEqualTo(70))));
    }

    @Test
    void evidenceLedgerListsCapturedEvidenceAcrossEvents() throws Exception {
        String firstUrl = "http://localhost:" + server.getAddress().getPort() + "/article";
        String policyUrl = "http://localhost:" + server.getAddress().getPort() + "/pboc-policy";

        ingestUrlAndWait(firstUrl, "Reuters", "宏观,市场");
        ingestUrlAndWait(policyUrl, "中国人民银行", "宏观,政策");

        mvc.perform(get("/api/evidence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data[*].sourceTier").value(hasItem("MEDIA")))
                .andExpect(jsonPath("$.data[*].sourceTier").value(hasItem("REGULATOR")))
                .andExpect(jsonPath("$.data[*].claim").value(hasItem(containsString("MLF"))));
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
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].eventId").value(2))
                .andExpect(jsonPath("$.data[0].sourceTier").value("REGULATOR"))
                .andExpect(jsonPath("$.data[0].evidenceType").value("TIMELINE"))
                .andExpect(jsonPath("$.data[0].confidence").value(greaterThanOrEqualTo(80)));

        mvc.perform(get("/api/evidence/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventId").value(2))
                .andExpect(jsonPath("$.data.claim", containsString("MLF")));
    }

    @Test
    void learningTasksAndContentIdeasAreGeneratedFromEvents() throws Exception {
        String firstUrl = "http://localhost:" + server.getAddress().getPort() + "/article";
        String followUpUrl = "http://localhost:" + server.getAddress().getPort() + "/fed-followup";

        ingestUrlAndWait(firstUrl, "Reuters", "宏观,市场");
        ingestUrlAndWait(followUpUrl, "CNBC", "宏观,市场");

        mvc.perform(get("/api/learning-tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data[0].eventId").value(1))
                .andExpect(jsonPath("$.data[0].themeCode").value("china_macro"))
                .andExpect(jsonPath("$.data[0].question").isNotEmpty())
                .andExpect(jsonPath("$.data[0].status").value("SUGGESTED"));

        mvc.perform(get("/api/content-ideas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data[0].eventId").value(1))
                .andExpect(jsonPath("$.data[0].title").isNotEmpty())
                .andExpect(jsonPath("$.data[0].angle").isNotEmpty())
                .andExpect(jsonPath("$.data[0].format").isNotEmpty())
                .andExpect(jsonPath("$.data[0].score").value(greaterThanOrEqualTo(60)))
                .andExpect(jsonPath("$.data[0].outline", containsString("1.")));
    }

    @Test
    void generatedResearchArtifactsUseEventContext() throws Exception {
        String marketReactionUrl = "http://localhost:" + server.getAddress().getPort() + "/article";
        String policyUrl = "http://localhost:" + server.getAddress().getPort() + "/pboc-policy";

        ingestUrlAndWait(marketReactionUrl, "Reuters", "宏观,市场");
        ingestUrlAndWait(policyUrl, "中国人民银行", "宏观,政策");

        mvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mvc.perform(get("/api/learning-tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].concepts").value(hasItem(containsString("黄金"))))
                .andExpect(jsonPath("$.data[*].concepts").value(hasItem(containsString("MLF"))))
                .andExpect(jsonPath("$.data[*].whyNeeded").value(hasItem(containsString("政策工具"))));

        mvc.perform(get("/api/content-ideas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].angle").value(hasItem(containsString("实际利率"))))
                .andExpect(jsonPath("$.data[*].angle").value(hasItem(containsString("政策工具"))))
                .andExpect(jsonPath("$.data[*].scoreReason").value(hasItem(containsString("政策信号"))));
    }

    @Test
    void learningTaskLifecycleCommandsAreValidated() throws Exception {
        seedResearchArtifacts();

        mvc.perform(post("/api/knowledge/topics")
                        .contentType("application/json")
                        .content("{\"name\":\"宏观政策\",\"description\":\"用于学习任务集成测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));

        mvc.perform(post("/api/knowledge/tasks/1/accept")
                        .contentType("application/json")
                        .content("{\"topicId\":1,\"expectedRevision\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.topicId").value(1))
                .andExpect(jsonPath("$.data.status").value("TODO"))
                .andExpect(jsonPath("$.data.revision").value(1));

        mvc.perform(post("/api/knowledge/tasks/1/start")
                        .contentType("application/json")
                        .content("{\"expectedRevision\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.revision").value(2));

        mvc.perform(post("/api/knowledge/tasks/1/start")
                        .contentType("application/json")
                        .content("{\"expectedRevision\":1}"))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/knowledge/tasks/999/start")
                        .contentType("application/json")
                        .content("{\"expectedRevision\":0}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FS-2001"))
                .andExpect(jsonPath("$.message", containsString("学习任务不存在")));

        mvc.perform(post("/api/knowledge/tasks/1/accept")
                        .contentType("application/json")
                        .content("{\"expectedRevision\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FS-1002"));
    }

    @Test
    void contentIdeaStatusAndDetailAreValidated() throws Exception {
        seedResearchArtifacts();

        mvc.perform(get("/api/content-ideas/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value(not(containsString("DRAFTING"))));

        mvc.perform(post("/api/content-ideas/1/status")
                        .contentType("application/json")
                        .content("{\"status\":\"DRAFTING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("DRAFTING"));

        mvc.perform(post("/api/content-ideas/1/status")
                        .contentType("application/json")
                        .content("{\"status\":\"INVALID\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FS-1002"))
                .andExpect(jsonPath("$.message", containsString("不支持的内容选题状态：INVALID")));

        mvc.perform(get("/api/content-ideas/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FS-2001"))
                .andExpect(jsonPath("$.message", containsString("内容选题不存在：999")));
    }

    @Test
    void contentIdeasCanBePagedFromBackend() throws Exception {
        seedResearchArtifacts();

        mvc.perform(get("/api/content-ideas/paged?page=0&pageSize=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.totalCount").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.pageSize").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.items[0].title").isNotEmpty());

        mvc.perform(get("/api/content-ideas/paged?page=-1&pageSize=20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FS-1002"))
                .andExpect(jsonPath("$.message").value("页码不能小于 0，且每页数量必须在 1 到 100 之间"));
    }

    @Test
    void briefIncludesResearchSectionsAndExposesResearchContext() throws Exception {
        String firstUrl = "http://localhost:" + server.getAddress().getPort() + "/article";
        String followUpUrl = "http://localhost:" + server.getAddress().getPort() + "/fed-followup";

        ingestUrlAndWait(firstUrl, "Reuters", "宏观,市场");
        ingestUrlAndWait(followUpUrl, "CNBC", "宏观,市场");

        mvc.perform(post("/api/briefs/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("每日金融、投资、创业学习简报 - " + LocalDate.now()))
                .andExpect(jsonPath("$.data.content", containsString("## 今日新变量")))
                .andExpect(jsonPath("$.data.content", containsString("## 事件追踪")))
                .andExpect(jsonPath("$.data.content", containsString("## 中国宏观")))
                .andExpect(jsonPath("$.data.content", containsString("## 今日证据来源")))
                .andExpect(jsonPath("$.data.content", containsString("## 今天要补的金融知识")))
                .andExpect(jsonPath("$.data.content", containsString("## 可发展为自媒体选题")))
                .andExpect(jsonPath("$.data.content", containsString("## 今日思考题")));

        mvc.perform(get("/api/briefs/" + LocalDate.now() + "/research-context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.briefDate").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.data.events.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.evidenceItems.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.learningTasks.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.contentIdeas.length()").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void researchRunExecutesEndToEndFromThemes() throws Exception {
        String macroSource = "{\"name\":\"Macro Source\",\"type\":\"RSS\",\"url\":\"" + rssUrl + "\",\"enabled\":true,"
                + "\"fetchFrequencyMinutes\":60,\"credibility\":5,\"tags\":\"china_macro,macro\"}";

        mvc.perform(post("/api/sources").contentType("application/json").content(macroSource))
                .andExpect(status().isOk());

        mvc.perform(post("/api/research/theses")
                        .contentType("application/json")
                        .content("{\"question\":\"宏观政策变化如何影响黄金？\",\"subjectType\":\"INDUSTRY\","
                                + "\"subjectName\":\"黄金\",\"subjectCode\":\"GOLD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));

        mvc.perform(post("/api/research/runs")
                        .contentType("application/json")
                        .content("{\"thesisId\":1,\"runDate\":\"" + LocalDate.now() + "\",\"themeCodes\":[\"china_macro\"],"
                                + "\"maxSourcesPerTheme\":1,\"includeDisabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.themeCodes.length()").value(1))
                .andExpect(jsonPath("$.data.sourceCount").value(1))
                .andExpect(jsonPath("$.data.fetchedSourceCount").value(0))
                .andExpect(jsonPath("$.data.plannedSources.length()").value(1))
                .andExpect(jsonPath("$.data.plannedSources[*].sourceName").value(hasItem("Macro Source")))
                .andExpect(jsonPath("$.data.summary", containsString("Planned 1 sources")));

        String completed = waitForResearchRun(1L);
        assertTrue(completed.contains("\"status\":\"COMPLETED\""));

        mvc.perform(get("/api/research/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data[0].sourceCount").value(1))
                .andExpect(jsonPath("$.data[0].eventCount").value(greaterThanOrEqualTo(1)));

        mvc.perform(get("/api/research/runs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.run.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.plannedSources.length()").value(1))
                .andExpect(jsonPath("$.data.plannedSources[*].sourceName").value(hasItem("Macro Source")))
                .andExpect(jsonPath("$.data.planSteps.length()").value(6))
                .andExpect(jsonPath("$.data.planSteps[*].stepId").value(hasItem("plan_sources")))
                .andExpect(jsonPath("$.data.planSteps[*].stepId").value(hasItem("fetch_sources")))
                .andExpect(jsonPath("$.data.planSteps[*].stepId").value(hasItem("compose_report")))
                .andExpect(jsonPath("$.data.planSteps[*].status").value(hasItem("COMPLETED")))
                .andExpect(jsonPath("$.data.agentRuns.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.agentRuns[*].nodeName").value(hasItem("research-orchestrate")))
                .andExpect(jsonPath("$.data.agentRuns[*].nodeName").value(hasItem("article-interpret")))
                .andExpect(jsonPath("$.data.agentRuns[*].nodeName").value(hasItem("evidence-extract")))
                .andExpect(jsonPath("$.data.agentRuns[*].nodeName").value(hasItem("learning-generate")))
                .andExpect(jsonPath("$.data.agentRuns[*].nodeName").value(hasItem("content-idea-generate")))
                .andExpect(jsonPath("$.data.agentRuns[*].researchRunId").value(hasItem(1)))
                .andExpect(jsonPath("$.data.reportAvailable").value(true))
                .andExpect(jsonPath("$.data.reportStatus", containsString("COMPLETED")))
                .andExpect(jsonPath("$.data.mission.mission.goal").value("宏观政策变化如何影响黄金？"))
                .andExpect(jsonPath("$.data.mission.tasks.length()").value(greaterThanOrEqualTo(6)));

        mvc.perform(get("/api/research/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[*].code").value(hasItem("public_news_search")));

        mvc.perform(get("/api/research/runs/1/mission"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mission.planningMode").value("DETERMINISTIC"))
                .andExpect(jsonPath("$.data.tasks.length()").value(greaterThanOrEqualTo(6)))
                .andExpect(jsonPath("$.data.gaps.length()").value(greaterThanOrEqualTo(1)));

        mvc.perform(get("/api/research/runs/1/runtime"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkpoint.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.events.length()").value(greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.data.recoverable").value(false));

        mvc.perform(post("/api/research/runs/1/evaluations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").isNumber())
                .andExpect(jsonPath("$.data.gateStatus").value("PASS"))
                .andExpect(jsonPath("$.data.metrics.length()").value(6));

        mvc.perform(get("/api/research/runs/1/evaluations/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inputFingerprint").value(matchesPattern("[0-9a-f]{64}")));

        mvc.perform(get("/api/research/runs/1/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.researchRunId").value(1))
                .andExpect(jsonPath("$.data.thesisId").value(1))
                .andExpect(jsonPath("$.data.status", containsString("COMPLETED")))
                .andExpect(jsonPath("$.data.title").isNotEmpty())
                .andExpect(jsonPath("$.data.contentMarkdown").isNotEmpty())
                .andExpect(jsonPath("$.data.evidenceCount").value(greaterThanOrEqualTo(1)));

        jdbcTemplate.update("UPDATE research_runtime_checkpoint SET status='INTERRUPTED',last_error='test restart' "
                + "WHERE research_run_id=1");
        jdbcTemplate.update("UPDATE research_run SET status='FAILED' WHERE id=1");
        mvc.perform(post("/api/research/runs/1/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RUNNING"));
        assertTrue(waitForResearchRun(1L).contains("\"status\":\"COMPLETED\""));
    }

    @Test
    void recommendedNewsSourcesCanBeInstalledIdempotently() throws Exception {
        mvc.perform(post("/api/sources/recommended-news"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[*].name").value(hasItem("BBC Business")))
                .andExpect(jsonPath("$.data[*].name").value(hasItem("Federal Reserve Press Releases")))
                .andExpect(jsonPath("$.data[*].name").value(hasItem("TechCrunch")))
                .andExpect(jsonPath("$.data[*].name").value(hasItem("Google News 中文科技与半导体")));

        mvc.perform(post("/api/sources/recommended-news"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4));

        mvc.perform(get("/api/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4));
    }

    @Test
    void deletingArticleCleansResearchArtifactsAndCounts() throws Exception {
        seedResearchArtifacts();

        mvc.perform(get("/api/events/1/evidence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mvc.perform(delete("/api/articles/batch")
                        .contentType("application/json")
                        .content("{\"ids\":[1]}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/events/1/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/api/events/1/evidence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/api/events/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.articleCount").value(1))
                .andExpect(jsonPath("$.data.evidenceCount").value(1));
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
                .andExpect(jsonPath("$.data.insightCard.oneSentenceSummary", containsString("Cloudflare 免费额度")))
                .andExpect(jsonPath("$.data.insightCard.importance", containsString("低成本独立开发")))
                .andExpect(jsonPath("$.data.insightCard.cardMarkdown", containsString("### 可沉淀主题")))
                .andExpect(jsonPath("$.data.insightCard.cardMarkdown", containsString("Cloudflare 免费基础设施实践")));

        mvc.perform(post("/api/topics/from-article/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Cloudflare 免费基础设施实践"))
                .andExpect(jsonPath("$.data.terms", containsString("Workers")))
                .andExpect(jsonPath("$.data.terms", containsString("D1")))
                .andExpect(jsonPath("$.data.learningQuestions", containsString("免费额度")))
                .andExpect(jsonPath("$.data.learningQuestions", containsString("供应商锁定")));

        mvc.perform(get("/api/agent-runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].nodeName").value("article-interpret"))
                .andExpect(jsonPath("$.data[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].input", containsString("fake-openai-model")))
                .andExpect(jsonPath("$.data[0].output", containsString("Cloudflare 免费基础设施实践")));
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
                .andExpect(jsonPath("$.code").value("FS-1002"))
                .andExpect(jsonPath("$.message").value("请求参数不合法"))
                .andExpect(jsonPath("$.traceId").value("test-trace-001"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void missingTopicReturnsNotFoundErrorCodeAndGeneratedRequestId() throws Exception {
        mvc.perform(get("/api/topics/9999"))
                .andExpect(status().isNotFound())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FS-2001"))
                .andExpect(jsonPath("$.message", containsString("主题不存在：9999")))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void exportPackageContainsManifestAndVault() throws Exception {
        mvc.perform(post("/api/briefs/generate"))
                .andExpect(status().isOk());

        String response = mvc.perform(post("/api/exports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.path", containsString("backup-")))
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
                .andExpect(jsonPath("$.data.articleId").value(1));
        mvc.perform(post("/api/briefs/generate"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/topics/from-article/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("美联储"))
                .andExpect(jsonPath("$.data.articleCount").value(1))
                .andExpect(jsonPath("$.data.terms", containsString("美联储")))
                .andExpect(jsonPath("$.data.learningQuestions", containsString("美联储")))
                .andExpect(jsonPath("$.data.markdownPath", containsString("vault/topics")));

        mvc.perform(post("/api/topics/from-brief/" + LocalDate.now()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)));

        mvc.perform(get("/api/topics/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.topic.name").value("美联储"))
                .andExpect(jsonPath("$.data.linkedArticles[0].title").value("美联储释放降息信号 黄金走强"))
                .andExpect(jsonPath("$.data.linkedBriefs.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.markdown", containsString("美联储")))
                .andExpect(jsonPath("$.data.markdown", containsString("### 核心事件")))
                .andExpect(jsonPath("$.data.markdown", containsString("### 后续观察")));

        mvc.perform(post("/api/topics/1/notes")
                        .contentType("application/json")
                        .content("{\"status\":\"REVIEWING\",\"note\":\"我理解的核心变量是利率预期。\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVIEWING"))
                .andExpect(jsonPath("$.data.markdownPath", containsString("vault/topics")));
        boolean noteWritten = Files.walk(Paths.get(dataRoot, "vault/topics"))
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
                .andExpect(jsonPath("$.data.articleId").value(1));
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
                .andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/articles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("美联储释放降息信号 黄金走强"));
        mvc.perform(get("/api/briefs/" + LocalDate.now()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title", containsString("每日")));
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
                .andExpect(jsonPath("$.data.taskId").isNotEmpty())
                .andReturn();
        return extractJsonString(submitted.getResponse().getContentAsString(), "taskId");
    }

    private String submitIngestTaskWithCategory(String url, String sourceName, String category) throws Exception {
        MvcResult submitted = mvc.perform(post("/api/articles/ingest-url")
                        .contentType("application/json")
                        .content("{\"url\":\"" + url + "\",\"sourceName\":\"" + sourceName
                                + "\",\"category\":\"" + category + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").isNotEmpty())
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
        for (int attempt = 0; attempt < 150; attempt++) {
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
                + "<rss version=\"2.0\"><channel>"
                + "<title>Empty Feed</title>"
                + "<link>https://example.com/empty</link>"
                + "<description>用于验证空候选处理的合法空订阅源</description>"
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
