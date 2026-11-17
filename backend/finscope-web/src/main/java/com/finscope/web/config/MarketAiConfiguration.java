package com.finscope.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class MarketAiConfiguration {
    @Bean(name = "marketAiExecutor")
    public ThreadPoolTaskExecutor marketAiExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("market-ai-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(2);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
