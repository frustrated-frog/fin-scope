package com.finscope.service.quant.overnight;

import com.finscope.domain.quant.overnight.OvernightAutomationContext;
import com.finscope.domain.quant.overnight.OvernightLedgerPosition;
import com.finscope.domain.strategy.holding.StockPosition;
import com.finscope.rpc.quant.PythonOvernightClient;
import com.finscope.service.strategy.holding.StockTransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.math.BigDecimal;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** 仅同步本地账本；分钟获取和研究计算由行情服务的持久化任务执行。 */
@Service
@Slf4j
public class OvernightAutomationScheduler {
    @Resource
    private StockTransactionService transactions;
    @Resource
    private PythonOvernightClient client;
    @Resource(name = "overnightLedgerScheduler")
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> heartbeat;
    @Value("${finscope.overnight-automation.enabled:true}")
    private boolean enabled;
    @Value("${finscope.overnight-automation.candidate-limit:6}")
    private int candidateLimit;
    @Value("${finscope.overnight-automation.tail-cost-bps:20}")
    private double tailCostBps;
    @Value("${finscope.overnight-automation.holding-cost-bps:10}")
    private double holdingCostBps;

    @PostConstruct
    public void start() {
        heartbeat = scheduler.scheduleWithFixedDelay(this::synchronizeLedger, 10, 60, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop() {
        if (heartbeat != null) {
            heartbeat.cancel(false);
        }
    }

    public void synchronizeLedger() {
        try {
            OvernightAutomationContext context = new OvernightAutomationContext();
            context.setEnabled(enabled);
            context.setCandidateLimit(candidateLimit);
            context.setTailCostBps(tailCostBps);
            context.setHoldingCostBps(holdingCostBps);
            for (StockPosition position : transactions.account().getPositions()) {
                if (position.getQuantity() == null || position.getQuantity().signum() <= 0) {
                    continue;
                }
                OvernightLedgerPosition value = new OvernightLedgerPosition();
                value.setInstrumentCode(position.getInstrumentCode());
                value.setInstrumentName(position.getInstrumentName());
                value.setQuantity(position.getQuantity());
                value.setAverageCost(position.getAverageCost() == null ? BigDecimal.ZERO : position.getAverageCost());
                value.setOpenedOn(position.getOpenedOn());
                context.getPositions().add(value);
            }
            client.syncAutomationContext(context);
        } catch (RuntimeException error) {
            log.warn("隔夜自动研究账本同步失败，下个周期重试：{}", error.getClass().getSimpleName());
        }
    }
}
