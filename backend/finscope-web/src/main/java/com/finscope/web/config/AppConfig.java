package com.finscope.web.config;

import com.finscope.service.brief.BriefGenerator;
import com.finscope.service.dedupe.FingerprintService;
import com.finscope.service.export.ExportService;
import com.finscope.service.vault.VaultWriter;
import com.finscope.rpc.llm.LlmChatClient;
import com.finscope.rpc.llm.OpenAiCompatibleLlmClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.annotation.Resource;
import java.nio.file.Paths;
import java.util.concurrent.Executor;

@Configuration
public class AppConfig {
    @Resource
    private FinScopeProperties properties;

    @Bean
    public FingerprintService fingerprintService() {
        return new FingerprintService();
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
}
