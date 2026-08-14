package com.finscope.service.quant.discovery;

import com.finscope.dao.quant.StockDiscoveryRepository;
import com.finscope.domain.quant.discovery.StockDiscoveryEventPublisher;
import com.finscope.domain.quant.discovery.StockDiscoveryReport;
import com.finscope.domain.quant.discovery.StockDiscoveryRequestedEvent;
import com.finscope.domain.quant.discovery.StockDiscoveryRun;
import com.finscope.rpc.quant.PythonStockDiscoveryClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class StockDiscoveryService {
    public static final String POLICY_VERSION = "stock-discovery-v1";
    @Resource
    private StockDiscoveryRepository repository;
    @Resource
    private StockDiscoveryEventPublisher publisher;
    @Resource
    private PythonStockDiscoveryClient client;
    @Resource(name = "stockDiscoveryExecutor")
    private Executor fallbackExecutor;

    public StockDiscoveryRun schedule(LocalDate businessDate, String triggerType) {
        String runKey = businessDate + ":" + POLICY_VERSION;
        StockDiscoveryRun run = repository.createIfAbsent(
                runKey, businessDate, 6000d, POLICY_VERSION, triggerType);
        if ("SUCCEEDED".equals(run.getStatus())) {
            return run;
        }
        StockDiscoveryRequestedEvent event = event(run);
        if (!publisher.publish(event)) {
            try {
                fallbackExecutor.execute(() -> execute(event));
            } catch (RuntimeException error) {
                log.warn("股票发现本地降级任务提交失败，runKey={}", run.getRunKey(), error);
            }
        }
        return repository.findById(run.getId()).orElse(run);
    }

    public void execute(StockDiscoveryRequestedEvent event) {
        StockDiscoveryRun current = repository.findById(event.getRunId()).orElse(null);
        if (current == null || "SUCCEEDED".equals(current.getStatus())) {
            return;
        }
        String attemptToken = UUID.randomUUID().toString();
        if (!repository.tryMarkRunning(event.getRunId(), attemptToken)) {
            return;
        }
        try {
            StockDiscoveryReport report = client.discover(
                    LocalDate.parse(event.getBusinessDate()), event.getBudget(), event.getPolicyVersion());
            repository.complete(event.getRunId(), attemptToken, report);
        } catch (RuntimeException error) {
            repository.fail(event.getRunId(), attemptToken, safe(error));
            throw error;
        }
    }

    public Optional<StockDiscoveryRun> latest() {
        return repository.findLatestSuccess();
    }

    public List<StockDiscoveryRun> history(int limit) {
        return repository.findRecent(limit);
    }

    public StockDiscoveryRun detail(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("股票发现批次不存在"));
    }

    private StockDiscoveryRequestedEvent event(StockDiscoveryRun run) {
        StockDiscoveryRequestedEvent value = new StockDiscoveryRequestedEvent();
        value.setEventId(UUID.randomUUID().toString());
        value.setRunId(run.getId());
        value.setRunKey(run.getRunKey());
        value.setBusinessDate(run.getBusinessDate().toString());
        value.setBudget(run.getBudget());
        value.setPolicyVersion(run.getPolicyVersion());
        return value;
    }

    private String safe(RuntimeException error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        return message.replace('\n', ' ').replace('\r', ' ').substring(0, Math.min(500, message.length()));
    }
}
