package com.finscope.service.marketpulse;

import com.finscope.dao.marketpulse.MarketPulseRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.time.*;

@Service
@Slf4j
public class MarketAiScheduler {
    @Resource
    private MarketPulseRepository snapshots;
    @Resource
    private MarketAiService service;
    @Resource
    private MarketAiProperties properties;

    @Scheduled(initialDelayString = "${finscope.market-ai.initial-delay-ms:45000}", fixedDelayString = "${finscope.market-ai.interval-ms:60000}")
    public void refresh() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            snapshots.findLatestWorkspace(LocalDate.now(ZoneId.of("Asia/Shanghai"))).ifPresent(snapshot -> {
                if (snapshot.getGeneratedAt() != null && !snapshot.getGeneratedAt().isBefore(snapshot.getBusinessDate().atTime(15, 0))) {
                    service.request(snapshot.getBusinessDate(), false);
                }
            });
        } catch (RuntimeException error) {
            log.error("市场AI研判调度失败，规则复盘继续可用", error);
        }
    }
}
