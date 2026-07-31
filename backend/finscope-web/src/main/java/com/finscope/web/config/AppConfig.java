package com.finscope.web.config;

import com.finscope.service.brief.BriefGenerator;
import com.finscope.dao.financials.FinancialInterpretationRepository;
import com.finscope.dao.marketintel.CapitalInterpretationRepository;
import com.finscope.service.dedupe.FingerprintService;
import com.finscope.service.export.ExportService;
import com.finscope.service.vault.VaultWriter;
import com.finscope.rpc.llm.LlmChatClient;
import com.finscope.rpc.llm.OpenAiCompatibleLlmClient;
import com.finscope.rpc.search.TavilyWebSearchClient;
import com.finscope.rpc.search.AnySearchWebSearchProvider;
import com.finscope.rpc.search.WebSearchProvider;
import com.finscope.service.search.evidence.SearchEvidenceGateway;
import com.finscope.service.search.evidence.SearchResultFusionService;
import com.finscope.service.search.evidence.SearchUrlCanonicalizer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.annotation.Resource;
import java.nio.file.Paths;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class AppConfig {
    @Resource
    private FinScopeProperties properties;

    @Bean
    public FingerprintService fingerprintService() {
        return new FingerprintService();
    }

    @Bean
    public com.finscope.service.radar.RadarTextAnalyzer radarTextAnalyzer(FingerprintService fingerprintService) {
        return new com.finscope.service.radar.RadarTextAnalyzer(fingerprintService);
    }

    @Bean
    public BriefGenerator briefGenerator() {
        return new BriefGenerator();
    }

    @Bean
    public VaultWriter vaultWriter() {
        return new VaultWriter(Paths.get(properties.getDataRoot()).resolve("vault"));
    }

    @Bean
    public ExportService exportService() {
        return new ExportService(Paths.get(properties.getDataRoot()));
    }

    @Bean
    public LlmChatClient llmChatClient() {
        FinScopeProperties.LlmProperties llm = properties.getLlm();
        return new OpenAiCompatibleLlmClient(
                llm.isEnabled(),
                llm.getBaseUrl(),
                llm.getApiKey(),
                llm.getModel(),
                llm.getTimeoutMs(),
                llm.getTemperature());
    }

    @Bean
    public TavilyWebSearchClient tavilyWebSearchProvider() {
        FinScopeProperties.SearchProperties search = properties.getSearch();
        return new TavilyWebSearchClient(search.isEnabled(), search.getApiKey());
    }

    @Bean
    public AnySearchWebSearchProvider anySearchWebSearchProvider() {
        FinScopeProperties.AnySearchProperties search = properties.getSearch().getAnySearch();
        return new AnySearchWebSearchProvider(search.isEnabled(), search.getApiKey(), search.getBaseUrl(),
                search.getTimeoutMs(), search.getMaxResponseBytes());
    }

    @Bean(name = "searchEvidenceExecutor", destroyMethod = "shutdownNow")
    public ExecutorService searchEvidenceExecutor() {
        int concurrency = Math.max(1, properties.getSearch().getFusion().getConcurrency());
        AtomicInteger sequence = new AtomicInteger();
        return Executors.newFixedThreadPool(concurrency, runnable -> {
            Thread thread = new Thread(runnable, "search-evidence-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    public SearchEvidenceGateway searchEvidenceGateway(
            TavilyWebSearchClient tavily,
            AnySearchWebSearchProvider anySearch,
            @Qualifier("searchEvidenceExecutor") ExecutorService executor) {
        FinScopeProperties.SearchFusionProperties fusion = properties.getSearch().getFusion();
        SearchResultFusionService fusionService = new SearchResultFusionService(
                new SearchUrlCanonicalizer(), fusion.getRrfConstant(), fusion.getMaxPerDomain());
        return new SearchEvidenceGateway(Arrays.<WebSearchProvider>asList(tavily, anySearch), executor, fusionService);
    }

    @Bean(name = "ingestTaskExecutor")
    public Executor ingestTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("ingest-task-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.initialize();
        return executor;
    }

    @Bean(name = "newsClassificationExecutor")
    public Executor newsClassificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("news-classification-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(20);
        executor.initialize();
        return executor;
    }

    @Bean(name = "quoteTaskExecutor")
    public Executor quoteTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("quote-task-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }

    @Bean(name = "marketDataGatewayExecutor")
    public Executor marketDataGatewayExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("market-data-gateway-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(32);
        executor.initialize();
        return executor;
    }

    @Bean(name = "marketDataFallbackExecutor")
    public Executor marketDataFallbackExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("market-data-fallback-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(32);
        executor.initialize();
        return executor;
    }

    @Bean(name = "marketDataWarmupExecutor")
    public Executor marketDataWarmupExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("market-data-warmup-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(8);
        executor.initialize();
        return executor;
    }

    @Bean(name = "attributionTaskExecutor")
    public Executor attributionTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("attribution-task-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(30);
        executor.initialize();
        return executor;
    }

    @Bean(name = "researchTaskExecutor")
    public Executor researchTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("research-task-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(20);
        executor.initialize();
        return executor;
    }

    @Bean(name = "quantExperimentExecutor")
    public Executor quantExperimentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("quant-experiment-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10);
        executor.initialize();
        return executor;
    }

    @Bean(name = "marketIntelRefreshExecutor")
    public Executor marketIntelRefreshExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("market-intel-refresh-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.initialize();
        return executor;
    }

    @Bean(name = "marketIntelAgentExecutor")
    public Executor marketIntelAgentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("market-intel-agent-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.initialize();
        return executor;
    }

    @Bean(name = "financialInterpretationExecutor")
    public Executor financialInterpretationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("financial-interpretation-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.initialize();
        return executor;
    }

    @Bean
    public ApplicationRunner agentInterpretationRecovery(
            FinancialInterpretationRepository financialInterpretations,
            CapitalInterpretationRepository capitalInterpretations) {
        return arguments -> {
            financialInterpretations.failInterrupted();
            capitalInterpretations.failInterrupted();
        };
    }
}
