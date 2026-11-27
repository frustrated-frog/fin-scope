package com.finscope.service.quant.study;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class AutonomousStudyScheduler {
    @Resource
    private AutonomousStudyService studies;
    @Resource(name = "quantStudyExecutor")
    private Executor executor;
    @Value("${finscope.quant.autonomous-study-enabled:true}")
    private boolean enabled;
    private final AtomicBoolean busy = new AtomicBoolean();

    @Scheduled(fixedDelayString = "${finscope.quant.autonomous-study-poll-ms:60000}", initialDelay = 35000)
    public void tick() {
        if (!enabled || !busy.compareAndSet(false,true)) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    studies.enqueue("daily:" + LocalDate.now(ZoneId.of("Asia/Shanghai")));
                    studies.workOne();
                } catch (RuntimeException error) {
                    log.warn("自主量化研究调度失败",error);
                } finally {
                    busy.set(false);
                }
            });
        } catch (RuntimeException error) {
            busy.set(false);
            throw error;
        }
    }
}
