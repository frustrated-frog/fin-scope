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
    private com.finscope.rpc.source.SourceAdapterRegistry adapterRegistry;
    @Resource
    private com.finscope.service.article.ArticleIngestCoordinator articleIngestCoordinator;
    @Resource
    private com.finscope.dao.source.SourceRepository sourceRepository;

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
                + "'event_cluster','evidence_item','learning_task','research_run','research_run_plan',"
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
        server.createContext("/research-rss-a", exchange -> {
            byte[] bytes = researchRss("support").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/rss+xml; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        server.createContext("/research-rss-b", exchange -> {
            byte[] bytes = researchRss("counter").getBytes(StandardCharsets.UTF_8);
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
    void investmentReactionRegistrationUsesRealSchemaAndIsoDates() throws Exception {
        jdbcTemplate.update("INSERT INTO major_event(origin_type,origin_key,title,summary,source_name,source_url,"
                        + "category_code,occurred_date,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                "NEWS_ITEM", "reaction-integration", "公司签订正式合同", "合同原文快照", "公告",
                "https://example.com/contract", "COMPANY", "2026-09-18", "2026-09-18T10:00:00", "2026-09-18T10:00:00");
        Long sourceId = jdbcTemplate.queryForObject("SELECT id FROM major_event WHERE origin_key=?", Long.class,
                "reaction-integration");
        MvcResult created = mvc.perform(post("/api/investment-reactions")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"majorEventId\":" + sourceId + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("DRAFT"))
                .andExpect(jsonPath("$.data.firstCapturedAt").value("2026-09-18T10:00:00"))
                .andReturn();
        long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        mvc.perform(post("/api/investment-reactions/" + id + "/confirm")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"instrumentCode\":\"600519.SH\",\"instrumentName\":\"示例公司\","
                                + "\"eventType\":\"CONTRACT\",\"publishedAt\":\"2026-09-18T09:00:00\","
                                + "\"relationNote\":\"公告披露主体\",\"revision\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("OBSERVING"));
        mvc.perform(get("/api/investment-reactions/" + id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sourceOriginType").value("NEWS_ITEM"))
                .andExpect(jsonPath("$.data.publishedAt").value("2026-09-18T09:00:00"))
                .andExpect(jsonPath("$.data.calculation").doesNotExist());
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
    void learningTasksAreGeneratedFromEvents() throws Exception {
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
    void studioEndpointsAreNoLongerAvailable() throws Exception {
        for (String path : new String[]{"/api/content-ideas", "/api/content-ideas/paged", "/api/content-ideas/1"}) {
            mvc.perform(get(path)).andExpect(status().isNotFound());
        }
        mvc.perform(post("/api/content-ideas/1/status")
                        .contentType("application/json")
                        .content("{\"status\":\"DRAFTING\"}"))
                .andExpect(status().isNotFound());
        assertTrue(jdbcTemplate.queryForList(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'content_idea'").isEmpty());
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
                .andExpect(jsonPath("$.data.content", not(containsString("## 可发展为自媒体选题"))))
                .andExpect(jsonPath("$.data.content", containsString("## 今日思考题")));

        mvc.perform(get("/api/briefs/" + LocalDate.now() + "/research-context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.briefDate").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.data.events.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.evidenceItems.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.learningTasks.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.contentIdeas").doesNotExist());
    }

    @Test
    void researchRunExecutesEndToEndFromThemes() throws Exception {
        String researchRssA = "http://localhost:" + server.getAddress().getPort() + "/research-rss-a";
        String researchRssB = "http://localhost:" + server.getAddress().getPort() + "/research-rss-b";
        String macroSource = "{\"name\":\"Macro Support\",\"type\":\"RSS\",\"url\":\"" + researchRssA + "\",\"enabled\":true,"
                + "\"fetchFrequencyMinutes\":60,\"credibility\":5,\"tags\":\"china_macro,macro\"}";
        String counterSource = "{\"name\":\"Macro Risk\",\"type\":\"RSS\",\"url\":\"" + researchRssB + "\",\"enabled\":true,"
                + "\"fetchFrequencyMinutes\":60,\"credibility\":5,\"tags\":\"china_macro,macro\"}";

        sourceRepository.save(new com.fasterxml.jackson.databind.ObjectMapper().readValue(macroSource, com.finscope.domain.source.Source.class));
        sourceRepository.save(new com.fasterxml.jackson.databind.ObjectMapper().readValue(counterSource, com.finscope.domain.source.Source.class));

        mvc.perform(post("/api/research/theses")
                        .contentType("application/json")
                        .content("{\"question\":\"宏观政策变化如何影响黄金？\",\"subjectType\":\"INDUSTRY\","
                                + "\"subjectName\":\"黄金\",\"subjectCode\":\"GOLD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));

        mvc.perform(post("/api/research/runs")
                        .contentType("application/json")
                        .content("{\"thesisId\":1,\"runDate\":\"" + LocalDate.now()
                                + "\",\"themeCodes\":[\"china_macro\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("DEEP"))
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.themeCodes.length()").value(1))
                .andExpect(jsonPath("$.data.sourceCount").value(2))
                .andExpect(jsonPath("$.data.fetchedSourceCount").value(0))
                .andExpect(jsonPath("$.data.plannedSources.length()").value(2))
                .andExpect(jsonPath("$.data.plannedSources[*].sourceName").value(hasItem("Macro Support")))
                .andExpect(jsonPath("$.data.plannedSources[*].sourceName").value(hasItem("Macro Risk")))
                .andExpect(jsonPath("$.data.summary", containsString("Planned 2 sources")));

        String completed = waitForResearchRun(1L);
        assertTrue(completed.contains("\"status\":\"COMPLETED\""));

        mvc.perform(get("/api/research/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].mode").value("DEEP"))
                .andExpect(jsonPath("$.data[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data[0].sourceCount").value(2))
                .andExpect(jsonPath("$.data[0].eventCount").value(greaterThanOrEqualTo(1)));

        mvc.perform(get("/api/research/runs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.run.mode").value("DEEP"))
                .andExpect(jsonPath("$.data.run.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.plannedSources.length()").value(2))
                .andExpect(jsonPath("$.data.plannedSources[*].sourceName").value(hasItem("Macro Support")))
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
                .andExpect(jsonPath("$.data.agentRuns[*].nodeName").value(not(hasItem("content-idea-generate"))))
                .andExpect(jsonPath("$.data.agentRuns[*].researchRunId").value(hasItem(1)))
                .andExpect(jsonPath("$.data.reportAvailable").value(true))
                .andExpect(jsonPath("$.data.reportStatus", containsString("COMPLETED")))
                .andExpect(jsonPath("$.data.mission.mission.goal").value("宏观政策变化如何影响黄金？"))
                .andExpect(jsonPath("$.data.mission.tasks.length()").value(greaterThanOrEqualTo(6)))
                .andExpect(jsonPath("$.data.agentCore.state.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.agentCore.decisions[*].decisionType").value(hasItem("FINISH")))
                .andExpect(jsonPath("$.data.agentCore.trajectoryMetrics.decisionValidityRate").value(1.0));

        mvc.perform(get("/api/research/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[*].code").value(hasItem("public_news_search")))
                .andExpect(jsonPath("$.data[*].code").value(hasItem("research_material_search")));

        mvc.perform(get("/api/research/runs/1/mission"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mission.planningMode").value("CONTROLLED"))
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
                .andExpect(jsonPath("$.data.metrics.length()").value(7))
                .andExpect(jsonPath("$.data.metrics[*].metricCode").value(hasItem("agent_trajectory")));

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
        com.finscope.domain.source.Source source = new com.finscope.domain.source.Source();
        source.setName(sourceName);
        source.setType("WEB");
        source.setUrl(url);
        source.setTags(tags);
        for (com.finscope.domain.fetch.RawItem item : adapterRegistry.get(source).fetch(source)) {
            articleIngestCoordinator.ingest(source, item);
        }
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

    private String researchRss(String stance) {
        boolean support = "support".equals(stance);
        String source = support ? "Support Desk" : "Risk Desk";
        String[][] items = support
                ? new String[][]{
                {"央行增持推动黄金需求持续增长", "多国央行持续增加黄金储备，长期需求保持增长。"},
                {"降息预期上修支持黄金景气", "实际利率下行预期增加，黄金市场景气度继续上修。"},
                {"黄金投资需求扩张并创阶段新高", "机构配置黄金的投资计划扩张，需求创下阶段新高。"}}
                : new String[][]{
                {"美元走强令黄金价格回落", "美元指数上行，黄金价格出现回落风险。"},
                {"黄金消费需求放缓且库存增加", "高金价限制消费，黄金需求放缓并出现库存压力。"},
                {"利率维持高位或导致黄金配置下调", "降息延迟可能带来黄金配置下调与资金流出风险。"},
                {"黄金矿产供应增加带来下滑风险", "矿产黄金供应扩张，需求放缓可能带来价格下滑风险。"}};
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<rss version=\"2.0\"><channel><title>").append(source).append("</title>");
        for (int index = 0; index < items.length; index++) {
            xml.append("<item><title>").append(items[index][0]).append("</title>")
                    .append("<link>https://example.com/research-").append(stance).append('-').append(index).append("</link>")
                    .append("<description>").append(items[index][1]).append("</description>")
                    .append("<pubDate>").append(DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC))).append("</pubDate>")
                    .append("</item>");
        }
        return xml.append("</channel></rss>").toString();
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
