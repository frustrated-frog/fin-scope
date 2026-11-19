package com.finscope.service.investmentobservation;

import com.fasterxml.jackson.databind.*;
import com.finscope.common.enums.investmentobservation.*;
import com.finscope.common.enums.news.NewsAiEventType;
import com.finscope.dao.fundamentals.FundamentalResearchRepository;
import com.finscope.dao.investmentobservation.*;
import com.finscope.dao.news.*;
import com.finscope.domain.fundamentals.CompanyBusinessProfile;
import com.finscope.domain.investmentobservation.*;
import com.finscope.domain.news.NewsReport;
import com.finscope.domain.news.ai.*;
import com.finscope.rpc.llm.NewsAiLunaClient;
import com.finscope.service.news.NewsAiProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ObservationNewsAttributionTest {
    @TempDir Path temp;
    private final LocalDateTime now = LocalDateTime.parse("2026-11-19T10:00");
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private JdbcTemplate jdbc;
    private final ObservationNewsAttributionService service = new ObservationNewsAttributionService();
    private final ReactionSampleRepository samples = new ReactionSampleRepository();
    private final NewsReportRepository reports = new NewsReportRepository();
    private final FundamentalResearchRepository profiles = new FundamentalResearchRepository();
    private final ReactionStockResolver resolver = mock(ReactionStockResolver.class);
    private final NewsAiProperties properties = new NewsAiProperties();
    private final AtomicInteger calls = new AtomicInteger();
    private HttpServer server;
    private static final String POLICY = "新政策支持高频印制电路板生产企业。";
    private static final String PRODUCT = "公司主营高频印制电路板。";

    @BeforeEach
    void setUp() throws Exception {
        var db = new SQLiteDataSource();
        db.setUrl("jdbc:sqlite:" + temp.resolve("news.db"));
        jdbc = new JdbcTemplate(db);
        var tx = new DataSourceTransactionManager(db);
        var reactionSchema = new ReactionSchemaMigrator();
        inject(reactionSchema,"jdbcTemplate",jdbc,"transactionManager",tx);
        reactionSchema.afterPropertiesSet();
        var newsSchema = new NewsWindowSchemaMigrator();
        inject(newsSchema,"jdbc",jdbc,"transactionManager",tx);
        newsSchema.afterPropertiesSet();
        var ledger = new NewsAiRepository();
        inject(ledger,"jdbc",jdbc,"mapper",json);
        ledger.afterPropertiesSet();
        jdbc.execute("CREATE TABLE instrument(id INTEGER PRIMARY KEY,code TEXT,name TEXT,market TEXT,type TEXT)");
        jdbc.execute("CREATE TABLE watchlist_item(instrument_id INTEGER)");
        jdbc.execute("CREATE TABLE company_business_profile(instrument_id INTEGER PRIMARY KEY,profile_json TEXT,updated_at TEXT)");
        inject(reports,"jdbc",jdbc,"mapper",json);
        inject(profiles,"jdbc",jdbc,"json",json);
        inject(samples,"jdbcTemplate",jdbc,"objectMapper",json);
        var repo = new ObservationNewsRepository();
        inject(repo,"jdbc",jdbc,"mapper",json,"samples",samples,"ledger",ledger);
        var proxy = new ProxyFactory(repo);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(tx,new AnnotationTransactionAttributeSource()));
        repo = (ObservationNewsRepository) proxy.getProxy();
        var verifier = new ObservationPolicyVerifier();
        var client = new NewsAiLunaClient();
        var config = new NewsAiProviderConfig();
        config.setApiKey("local-test");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/responses",exchange -> {
            calls.incrementAndGet();
            var request = json.readTree(exchange.getRequestBody());
            var payload = json.readTree(request.path("input").get(1).path("content").asText());
            var rows = new ArrayList<Map<String,Object>>();
            for (var company : payload.path("companies")) {
                boolean matches = company.path("business").asText().contains(PRODUCT);
                rows.add(Map.of("code",company.path("code").asText(),"matches",matches,"news",matches ? POLICY : "",
                        "business",matches ? PRODUCT : "","reason",matches ? "已披露主营产品属于政策适用范围" : ""));
            }
            byte[] result = json.writeValueAsBytes(Map.of("status","completed","usage",Map.of("input_tokens",120,"output_tokens",80),
                    "output",List.of(Map.of("type","message","content",List.of(Map.of("type","output_text","text",json.writeValueAsString(Map.of("decisions",rows))))))));
            exchange.sendResponseHeaders(200,result.length);
            exchange.getResponseBody().write(result);
            exchange.close();
        });
        server.start();
        config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setEnabled(true);
        inject(client,"mapper",json,"config",config);
        inject(verifier,"client",client,"properties",properties,"repository",repo,"ledger",ledger,"mapper",json);
        inject(service,"repository",repo,"policies",verifier,"resolver",resolver,"fundamentals",profiles,"properties",properties);
    }

    @AfterEach
    void close() {
        server.stop(0);
    }

    @Test
    void policyMatchesTwoStocksIncrementallyAndRemovesObsoleteEvidence() throws Exception {
        var analysis = new NewsAiAnalysis();
        analysis.setEventType(NewsAiEventType.POLICY);
        analysis.setPolicyProducts(List.of("印制电路板"));
        addCompany(1,"605058","澳弘电子",PRODUCT);
        addCompany(2,"000823","超声电子","公司主营普通印制电路板。");
        publish("a","政策发布",POLICY,analysis,"policy","NEW_EVENT");
        publish("reprint","转载政策",POLICY,analysis,"policy","DUPLICATE");
        assertEquals(1,service.refresh(now));
        assertEquals(1,calls.get());
        assertEquals(0,service.refresh(now));
        assertEquals(1,calls.get());
        updateProfile(2,PRODUCT);
        assertEquals(1,service.refresh(now));
        var matched = samples.findByIdentity("NEWS_EVENT:policy");
        assertEquals(2,matched.size());
        assertTrue(matched.stream().allMatch(s -> !s.isExcluded() && s.getEventType()==ReactionEventType.POLICY
                && s.getRuleEvidence().equals(POLICY) && s.getRelationNote().contains(PRODUCT)));
        var queryRepository = new ReactionEventQueryRepository();
        inject(queryRepository,"jdbcTemplate",jdbc,"samples",samples);
        var query = new ReactionEventQuery();
        query.setStock("000823.SZ");
        assertEquals(List.of("政策发布"),queryRepository.query(query).getItems().stream().map(ReactionSample::getTitle).toList());
        assertEquals(2,calls.get());
        updateProfile(1,"公司主营照明器材。");
        assertEquals(0,service.refresh(now));
        assertEquals(2,calls.get());
        var first = samples.findByIdentity("NEWS_EVENT:policy").stream().filter(s -> s.getInstrumentCode().equals("605058.SH")).findFirst().orElseThrow();
        assertTrue(first.isExcluded());
        assertTrue(samples.exclude(first.getId(),first.getRevision(),true));
        updateProfile(1,PRODUCT);
        service.refresh(now);
        assertTrue(samples.findById(first.getId()).orElseThrow().isExcluded());
        assertEquals(2,calls.get());
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM news_ai_call WHERE state='SETTLED'",Integer.class));
    }

    @Test
    void bodyNamesActualWinnerAndCorrectionInvalidatesOldStockBeforeNextUnderstanding() throws Exception {
        properties.setEnabled(false);
        var direct = new NewsAiAnalysis();
        direct.setEventType(NewsAiEventType.TRANSACTION);
        String fact = "雷神科技中标本次台式机集采。";
        direct.setCompanyActions(List.of(new NewsCompanyAction("雷神科技","中标方",fact),
                new NewsCompanyAction("另一公司","中标方","另一公司也中标。")));
        var resolution = new ReactionStockResolution();
        var match = new ReactionStockMatch();
        match.setCode("920190.BJ");
        match.setName("雷神科技");
        resolution.setStatus(ReactionResolutionStatus.RESOLVED);
        resolution.setMatches(List.of(match));
        when(resolver.resolveDirect(any())).thenReturn(resolution);
        publish("r","中央国家机关公布集采结果",fact + "另一公司曾参与投标。",direct,"procure","NEW_EVENT");
        assertEquals(1,service.refresh(now));
        assertEquals(0,service.refresh(now));
        verify(resolver,times(1)).resolveDirect(any());
        var sample = samples.findByIdentity("NEWS_EVENT:procure").getFirst();
        assertEquals(fact,sample.getRuleEvidence());
        assertEquals("920190.BJ",sample.getInstrumentCode());
        jdbc.update("UPDATE news_report SET content='更正：中标方另有其人',content_version=2 WHERE id='r'");
        service.refresh(now);
        assertTrue(samples.findById(sample.getId()).orElseThrow().isExcluded());
        assertEquals(0,calls.get());
    }

    @Test
    void legacyFactCardForActualProcurementReportCanBeProjectedWithoutAnotherModelCall() throws Exception {
        properties.setEnabled(false);
        String title = "雷神科技中标中央国家机关台式机批量集采";
        String fact = "雷神科技中标中央国家机关2026年11月台式计算机批量集中采购项目。";
        var analysis = new NewsAiAnalysis();
        analysis.setEventType(NewsAiEventType.TRANSACTION);
        analysis.setEntities(List.of("雷神科技","中央政府采购网"));
        analysis.setFacts(List.of(fact));
        publish("old",title,"近日，中央政府采购网发布的中标公告显示，" + fact,analysis,"purchase","NEW_EVENT");
        jdbc.update("UPDATE news_ai_binding SET payload=json_remove(payload,'$.analysis.companyActions','$.analysis.policyProducts')");
        var stockResolver = new ReactionStockResolver();
        var net = mock(com.finscope.rpc.marketintel.FinanceHttpClient.class);
        var lookup = new com.finscope.rpc.investmentobservation.ReactionStockNameLookup();
        inject(lookup,"http",net);
        when(net.get(anyString(),any(),anyMap(),anyInt(),anyInt())).thenReturn(new com.finscope.rpc.marketintel.FinanceHttpResponse(
                200,"""
                {"QuotationCodeTable":{"Status":0,"Data":[{"Code":"920190","Name":"雷神科技","Classify":"NEEQ","SecurityTypeName":"京A"}]}}
                """,java.time.Instant.now(),"fixture"));
        inject(stockResolver,"names",lookup,"instruments",mock(com.finscope.dao.instrument.InstrumentRepository.class));
        inject(service,"resolver",stockResolver);
        assertEquals(1,service.refresh(now));
        assertEquals("920190.BJ",samples.findByIdentity("NEWS_EVENT:purchase").getFirst().getInstrumentCode());
        assertEquals(0,service.refresh(now));
        assertEquals(0,calls.get());
    }

    private void addCompany(int id,String code,String name,String description) {
        jdbc.update("INSERT INTO instrument VALUES(?,?,?,?,?)",id,code,name,id==1 ? "SH" : "SZ","STOCK");
        jdbc.update("INSERT INTO watchlist_item VALUES(?)",id);
        updateProfile(id,description);
    }

    private void updateProfile(long id,String description) {
        var profile = new CompanyBusinessProfile();
        profile.setInstrumentId(id);
        profile.setDescription(description);
        profile.setFetchedAt(now);
        profile.setSourceUrl("https://example.test/company/" + id);
        profiles.saveProfile(profile);
    }

    private void publish(String id,String title,String text,NewsAiAnalysis analysis,String event,String relation) throws Exception {
        var report = new NewsReport();
        report.setId(id);
        report.setTitle(title);
        report.setContent(text);
        report.setKind("FLASH");
        report.setProviderCode("TEST");
        report.setSourceName("测试");
        report.setPublishedAt(now.minusMinutes(1));
        report.setFirstSeenAt(now);
        report.setLastSeenAt(now);
        reports.ingest(List.of(report));
        jdbc.update("INSERT INTO news_ai_report VALUES(?,?,?,?,1)",id,id,event,relation);
        jdbc.update("INSERT INTO news_ai_occurrence_job VALUES(?,?)",id,id);
        jdbc.update("INSERT INTO news_ai_binding VALUES(?,?)",id,json.writeValueAsString(Map.of("analysis",analysis,"eventKey",event)));
    }

    private void inject(Object target,Object... args) {
        for (int i=0; i<args.length; i+=2) {
            ReflectionTestUtils.setField(target,(String) args[i],args[i+1]);
        }
    }
}
