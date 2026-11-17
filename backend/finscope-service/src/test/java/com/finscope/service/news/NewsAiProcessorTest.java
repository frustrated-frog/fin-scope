package com.finscope.service.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.news.*;
import com.finscope.dao.news.*;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.news.*;
import com.finscope.domain.news.ai.*;
import com.finscope.domain.radar.RadarSignal;
import com.finscope.rpc.llm.NewsAiLunaClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.sqlite.SQLiteDataSource;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 本地 HTTP + SQLite 验证真实请求、解析、账本、缓存与原文写回，不访问付费模型。 */
class NewsAiProcessorTest {
    @TempDir Path directory;
    private final LocalDateTime now = LocalDateTime.parse("2026-11-17T10:00:00");
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final AtomicInteger calls = new AtomicInteger();
    private NewsAiProcessor processor;
    private NewsAiRepository repository;
    private NewsReportRepository reports;
    private NewsAiProperties properties;
    private NewsAiProviderConfig provider;
    private NewsAiUnifiedAgent agent;
    private JdbcTemplate jdbc;
    private HttpServer server;
    private final List<JsonNode> requests = new ArrayList<>();
    private Function<JsonNode, String> responder;

    @BeforeEach
    void setup() throws Exception {
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve("news.db"));
        jdbc = new JdbcTemplate(source);
        NewsWindowSchemaMigrator migration = new NewsWindowSchemaMigrator();
        ReflectionTestUtils.setField(migration, "jdbc", jdbc);
        ReflectionTestUtils.setField(migration, "transactionManager", new DataSourceTransactionManager(source));
        migration.afterPropertiesSet();
        repository = new NewsAiRepository();
        inject(repository, "jdbc", jdbc, "mapper", mapper);
        repository.afterPropertiesSet();
        reports = new NewsReportRepository();
        inject(reports, "jdbc", jdbc, "mapper", mapper);
        properties = new NewsAiProperties();
        properties.setEnabled(true);
        properties.setBackfillExisting(true);
        provider = new NewsAiProviderConfig();
        provider.setApiKey("local-test-only");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        responder = input -> decisions(input, false, false);
        server.createContext("/responses", exchange -> {
            JsonNode root = mapper.readTree(exchange.getRequestBody());
            JsonNode input = mapper.readTree(root.path("input").get(1).path("content").asText());
            requests.add(input);
            calls.incrementAndGet();
            String text = responder.apply(input);
            byte[] reply = mapper.writeValueAsBytes(Map.of("status", "completed", "usage", Map.of("input_tokens", 100, "output_tokens", 200),
                    "output", List.of(Map.of("type", "message", "content", List.of(Map.of("type", "output_text", "text", text))))));
            exchange.sendResponseHeaders(200, reply.length);
            exchange.getResponseBody().write(reply);
            exchange.close();
        });
        server.start();
        NewsAiLunaClient client = new NewsAiLunaClient();
        inject(client, "config", provider, "mapper", mapper);
        agent = new NewsAiUnifiedAgent();
        inject(agent, "client", client, "mapper", mapper, "properties", properties);
        NewsAiCandidateSelector selector = new NewsAiCandidateSelector();
        inject(selector, "ai", repository, "radar", mock(RadarRepository.class));
        NewsCategoryRepository categories = mock(NewsCategoryRepository.class);
        NewsCategory category = new NewsCategory("COMPANY", "公司", "公司经营披露", true, 1);
        when(categories.findEnabled()).thenReturn(List.of(category));
        processor = new NewsAiProcessor();
        inject(processor, "repository", repository, "reports", reports, "categories", categories,
                "agent", agent, "client", client, "properties", properties, "selector", selector);
    }

    @AfterEach
    void close() {
        server.stop(0);
    }

    @Test
    void exactContentAcrossSourcesAndRestartsCostsOneUnderstanding() {
        var one = signal("a", "公司签订合同", "合同金额2亿元");
        var two = signal("b", one.getTitle(), one.getContent());
        persist(one, two);
        processor.process(List.of(one, two), now);
        processor.process(List.of(one, two), now.plusMinutes(1));
        assertEquals(1, calls.get());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM news_ai_report", Integer.class));
        assertEquals("COMPANY", reports.find("a").orElseThrow().getCategoryCode());
        assertEquals(110L, ((Number) repository.dailyUsage(now).get("actual_micros")).longValue());
        assertEquals(0L, ((Number) repository.dailyUsage(now).get("unresolved_micros")).longValue());
    }

    @Test
    void masterOffMissingKeyAndZeroBudgetNeverSendRequests() {
        var one = signal("a", "公司签订合同", "合同金额2亿元");
        persist(one);
        properties.setEnabled(false);
        processor.process(List.of(one), now);
        properties.setEnabled(true);
        provider.setApiKey("");
        processor.process(List.of(one), now);
        provider.setApiKey("local-test-only");
        properties.setDailyBudgetMicros(0);
        processor.process(List.of(one), now);
        assertEquals(0, calls.get());
        assertTrue(repository.bindingsByHashes(List.of(key(one))).isEmpty());
        assertTrue(reports.find("a").isPresent());
        assertEquals(0, jdbc.queryForObject("SELECT attempt FROM news_ai_content", Integer.class));
    }

    @Test
    void noHistoricalBackfillUntilExplicitlyEnabledAndReenableStartsNewCutoff() {
        properties.setBackfillExisting(false);
        var old = signal("old", "旧新闻", "旧内容");
        persist(old);
        processor.process(List.of(old), now.plusMinutes(1));
        assertEquals(0, calls.get());
        var fresh = signal("new", "新新闻", "新内容");
        persistAt(fresh, now.plusMinutes(2));
        processor.process(List.of(old, fresh), now.plusMinutes(3));
        assertEquals(1, calls.get());
        properties.setEnabled(false);
        processor.process(List.of(old, fresh), now.plusMinutes(4));
        var offline = signal("offline", "停用期间", "另一条新闻");
        persistAt(offline, now.plusMinutes(5));
        properties.setEnabled(true);
        processor.process(List.of(old, fresh, offline), now.plusMinutes(6));
        assertEquals(1, calls.get());
    }

    @Test
    void sameBatchAliasesMergeRewritesWithoutAdvancingSemanticVersion() {
        responder = input -> decisions(input, true, false);
        var one = signal("a", "公司签订合同", "合同金额2亿元");
        var two = signal("b", "公司披露订单", "合同金额为2亿元，系同一合同");
        persist(one, two);
        processor.process(List.of(one, two), now);
        var bindings = repository.bindingsByHashes(List.of(key(one), key(two)));
        assertEquals(bindings.get(key(one)).getEventKey(), bindings.get(key(two)).getEventKey());
        assertEquals(1, repository.event(bindings.get(key(one)).getEventKey()).getRevision());
        assertEquals(1, calls.get());
        NewsWindowQuery query = new NewsWindowQuery();
        query.setAsOfSequence(reports.latestSequence());
        var page = reports.query(query, now.minusHours(1), now);
        assertEquals(1, page.getTotal());
        assertEquals(2, page.getItems().getFirst().getMergedReportCount());
        properties.setEnabled(false);
        processor.process(List.of(one, two), now.plusMinutes(1));
        assertEquals(1, reports.query(query, now.minusHours(1), now).getTotal());
    }

    @Test
    void onlyInvalidItemRetriesAndTruncatedCompleteItemsSurvive() {
        responder = input -> decisions(input, false, true);
        var one = signal("a", "公司签订合同", "合同金额2亿元");
        var two = signal("b", "新产品发布", "技术规格升级");
        persist(one, two);
        processor.process(List.of(one, two), now);
        assertEquals(1, repository.bindingsByHashes(List.of(key(one), key(two))).size());
        processor.process(List.of(one, two), now.plusMinutes(30));
        assertEquals(1, calls.get());
        responder = input -> decisions(input, false, false);
        processor.process(List.of(one, two), now.plusHours(1));
        assertEquals(2, calls.get());
        assertEquals(1, requests.get(1).path("items").size());
        assertEquals(2, repository.bindingsByHashes(List.of(key(one), key(two))).size());
    }

    @Test
    void httpFailureHasNoAutomaticResendAndKeepsConservativeReservation() {
        server.removeContext("/responses");
        server.createContext("/responses", exchange -> {
            calls.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        var one = signal("a", "公司签订合同", "合同金额2亿元");
        persist(one);
        processor.process(List.of(one), now);
        processor.process(List.of(one), now.plusHours(1));
        assertEquals(1, calls.get());
        assertTrue(((Number) repository.dailyUsage(now).get("unresolved_micros")).longValue() > 0);
        assertEquals("UNKNOWN", jdbc.queryForObject("SELECT state FROM news_ai_content", String.class));
    }

    @Test
    void anotherDayReusesUnderstandingButMakesANewOccurrenceDecision() {
        var one = signal("a", "市场收盘", "指数上涨1%");
        persist(one);
        processor.process(List.of(one), now);
        var tomorrow = signal("b", one.getTitle(), one.getContent());
        tomorrow.setPublishedAt(one.getPublishedAt().plusDays(1));
        persistAt(tomorrow, now.plusDays(1));
        processor.process(List.of(tomorrow), now.plusDays(1));
        assertEquals(2, calls.get());
        JsonNode second = requests.get(1).path("items").get(0);
        assertEquals("", second.path("content").asText());
        assertFalse(second.path("savedAnalysis").isNull());
        var bindings = repository.bindingsByHashes(List.of(key(one), key(tomorrow)));
        assertNotEquals(bindings.get(key(one)).getEventKey(), bindings.get(key(tomorrow)).getEventKey());
    }

    @Test
    void changedCandidateRetriesOnlyMatchingUsingSavedUnderstanding() throws Exception {
        var original = signal("a", "公司签订合同", "合同金额2亿元");
        persist(original);
        processor.process(List.of(original), now);
        String eventKey = repository.bindingsByHashes(List.of(key(original))).get(key(original)).getEventKey();
        var update = signal("b", "公司合同已交付", "合同完成交付");
        persist(update);
        var mutate = new java.util.concurrent.atomic.AtomicBoolean(true);
        responder = input -> {
            try {
                JsonNode result = mapper.readTree(decisions(input, false, false));
                var decision = (com.fasterxml.jackson.databind.node.ObjectNode) result.path("decisions").get(0);
                decision.put("targetKey", eventKey);
                decision.put("relation", "PROGRESS");
                decision.set("newFacts", mapper.valueToTree(List.of("合同完成交付")));
                if (mutate.getAndSet(false)) {
                    var current = repository.event(eventKey);
                    current.setRevision(current.getRevision() + 1);
                    jdbc.update("UPDATE news_ai_event SET revision=?,payload=? WHERE event_key=?",
                            current.getRevision(), mapper.writeValueAsString(current), eventKey);
                }
                return mapper.writeValueAsString(result);
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
        };
        processor.process(List.of(update), now.plusMinutes(1));
        assertTrue(repository.bindingsByHashes(List.of(key(update))).isEmpty());
        processor.process(List.of(update), now.plusMinutes(7));
        assertEquals(3, calls.get());
        assertEquals("", requests.get(2).path("items").get(0).path("content").asText());
        assertEquals(eventKey, repository.bindingsByHashes(List.of(key(update))).get(key(update)).getEventKey());
        assertEquals(3, repository.event(eventKey).getRevision());
    }

    @Test
    void staleOriginalAndManualCategoryCannotBeOverwrittenByModel() {
        var one = signal("a", "公司签订合同", "合同金额2亿元");
        persist(one);
        responder = input -> {
            jdbc.update("UPDATE news_report SET content='修订原文',content_version=2 WHERE id='a'");
            return decisions(input, false, false);
        };
        processor.process(List.of(one), now);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM news_ai_report", Integer.class));
        assertNull(reports.find("a").orElseThrow().getCategoryCode());
        var two = signal("b", "独立新闻", "独立正文");
        persist(two);
        reports.review("b", "MANUAL", "人工分类", "人工判断");
        responder = input -> decisions(input, false, false);
        processor.process(List.of(two), now);
        assertEquals("MANUAL", reports.find("b").orElseThrow().getCategoryCode());
    }

    private String decisions(JsonNode input, boolean merge, boolean truncate) {
        List<Map<String, Object>> decisions = new ArrayList<>();
        String first = input.path("items").get(0).path("contentKey").asText();
        int index = 0;
        for (JsonNode item : input.path("items")) {
            Map<String, Object> decision = new LinkedHashMap<>();
            decision.put("contentKey", item.path("contentKey").asText());
            decision.put("analysis", item.path("savedAnalysis").isNull() ? analysis() : null);
            decision.put("targetKey", merge && index > 0 ? "news-ai:" + first : "");
            decision.put("relation", merge && index > 0 ? "DUPLICATE" : "NEW_EVENT");
            decision.put("newFacts", List.of());
            decision.put("reason", "根据原文事实判断");
            decisions.add(decision);
            index++;
        }
        try {
            if (truncate) {
                return "{\"decisions\":[" + mapper.writeValueAsString(decisions.getFirst()) + ",{\"contentKey\":\"unfinished";
            }
            return mapper.writeValueAsString(Map.of("decisions", decisions));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private NewsAiAnalysis analysis() {
        var value = new NewsAiAnalysis();
        value.setCategoryCode("COMPANY");
        value.setDisplayCategory(NewsAiDisplayCategory.FINANCE);
        value.setEventType(NewsAiEventType.TRANSACTION);
        value.setTitle("公司发布进展");
        value.setSummary("原文披露业务进展");
        value.setFacts(List.of("披露业务进展"));
        return value;
    }

    private RadarSignal signal(String id, String title, String content) {
        return RadarSignal.builder().itemId(id).title(title).content(content).publishedAt(now.minusMinutes(1))
                .firstSeenAt(now).sourceName("财联社").providerCode("CLS").build();
    }

    private String key(RadarSignal signal) {
        return NewsAiContentIdentity.occurrence(signal.getTitle(), signal.getContent(), signal.getPublishedAt());
    }

    private void persist(RadarSignal... signals) {
        for (RadarSignal signal : signals) {
            persistAt(signal, now);
        }
    }

    private void persistAt(RadarSignal signal, LocalDateTime at) {
        NewsReport report = new NewsReport();
        report.setId(signal.getItemId());
        report.setTitle(signal.getTitle());
        report.setContent(signal.getContent());
        report.setPublishedAt(signal.getPublishedAt());
        report.setFirstSeenAt(at);
        report.setLastSeenAt(at);
        report.setProviderCode("CLS");
        report.setSourceName("财联社");
        report.setKind("FLASH");
        reports.ingest(List.of(report));
    }

    private void inject(Object target, Object... fields) {
        for (int index = 0; index < fields.length; index += 2) {
            ReflectionTestUtils.setField(target, (String) fields[index], fields[index + 1]);
        }
    }
}
