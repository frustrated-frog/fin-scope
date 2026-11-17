package com.finscope.web.config;

import com.finscope.domain.news.ai.NewsAiProviderConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NewsAiConfiguration {
    @Bean(name = "newsAiExecutor")
    public org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor newsAiExecutor() {
        var executor = new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("news-ai-"); executor.setCorePoolSize(1); executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1); executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }

    @Bean
    @ConfigurationProperties(prefix = "finscope.news-ai.provider")
    public NewsAiProviderConfig newsAiProviderConfig() {
        return new NewsAiProviderConfig();
    }
}
