package com.finscope.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class OvernightAutomationConfiguration {
    /** 账本心跳不能被文章抓取、股票发现结算等较慢的定时任务阻塞。 */
    @Bean(name = "overnightLedgerScheduler", destroyMethod = "shutdown")
    public ScheduledExecutorService overnightLedgerScheduler() {
        return Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "overnight-ledger-sync");
            thread.setDaemon(true);
            return thread;
        });
    }
}
