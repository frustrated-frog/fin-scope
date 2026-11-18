package com.finscope.service.marketpulse;

import com.finscope.common.enums.marketpulse.MarketAiStatus;
import com.finscope.dao.marketpulse.MarketAiRepository;
import com.finscope.dao.marketpulse.MarketPulseRepository;
import com.finscope.domain.marketpulse.MarketPulseWorkspace;
import com.finscope.domain.marketpulse.ai.*;
import com.finscope.rpc.llm.LunaResponsesClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class MarketAiService {
    @Resource
    private MarketAiRepository repository;
    @Resource
    private MarketPulseRepository snapshots;
    @Resource
    private MarketAiEvidenceService evidence;
    @Resource
    private MarketAiAgent agent;
    @Resource
    private LunaResponsesClient client;
    @Resource
    private MarketAiProperties properties;
    @Resource(name = "marketAiExecutor")
    private Executor executor;

    public MarketAiView read(LocalDate date) {
        MarketAiInput input = input(date);
        MarketAiView base = base(input);
        if (base.getStatus() != MarketAiStatus.NOT_GENERATED) {
            return reviewed(base, input);
        }
        return reviewed(repository.find(key(input)).orElse(base), input);
    }

    public MarketAiView request(LocalDate date, boolean retry) {
        MarketAiInput input = input(date);
        MarketAiView view = base(input);
        if (view.getStatus() != MarketAiStatus.NOT_GENERATED) {
            return view;
        }
        String key = key(input);
        repository.recover(now().minusMinutes(5));
        if (retry) {
            repository.retryFailed(key);
        }
        var existing = repository.find(key);
        if (existing.isPresent()) {
            return existing.get();
        }
        view.setStatus(MarketAiStatus.QUEUED);
        view.setMessage("正在整理当日市场与行业变化");
        String owner = UUID.randomUUID().toString();
        if (!repository.claim(key, owner, view, now())) {
            return repository.find(key).orElse(view);
        }
        try {
            executor.execute(() -> generate(key, owner, input, view));
        } catch (RuntimeException rejected) {
            view.setStatus(MarketAiStatus.FAILED);
            view.setMessage("研判队列暂忙，请稍后重试");
            repository.finish(key, owner, view, now());
        }
        return repository.find(key).orElse(view);
    }

    private void generate(String key, String owner, MarketAiInput input, MarketAiView view) {
        boolean sent = false;
        try {
            if (!properties.isEnabled()) {
                view.setStatus(MarketAiStatus.FAILED);
                view.setMessage("市场AI已关闭，继续显示规则速读");
                repository.finish(key, owner, view, now());
                return;
            }
            var prepared = agent.prepare(input);
            view.setStatus(MarketAiStatus.RUNNING);
            sent = repository.markSent(key, owner, view, now());
            if (!sent) {
                return;
            }
            var reply = client.complete(prepared);
            view.setInputTokens(reply.getInputTokens());
            view.setOutputTokens(reply.getOutputTokens());
            // 已收到回复但格式不合法，可以显式重试；不在客户端暗中多次请求。
            sent = false;
            view.setReport(agent.parse(reply.getText(), input));
            evidence.applyAssessment(view, input);
            view.setStatus(MarketAiStatus.READY);
            view.setGeneratedAt(now());
            view.setMessage(null);
            repository.finish(key, owner, view, now());
            log.info("市场AI研判完成 date={} inputTokens={} outputTokens={}", input.getBusinessDate(), reply.getInputTokens(), reply.getOutputTokens());
        } catch (Exception error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            view.setStatus(sent ? MarketAiStatus.UNKNOWN : MarketAiStatus.FAILED);
            view.setMessage(sent ? "本次研判请求结果未确认，当前保留规则速读" : "本次研判未完成，可重试；当前保留规则速读");
            repository.finish(key, owner, view, now());
            log.warn("市场AI研判未完成 date={} status={} errorType={}", input.getBusinessDate(), view.getStatus(), error.getClass().getSimpleName());
        }
    }

    private MarketAiInput input(LocalDate date) {
        if (date.isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai")))) {
            throw new IllegalArgumentException("不能读取未来日期的研判");
        }
        MarketPulseWorkspace workspace = snapshots.findWorkspace(date).orElseThrow(() -> new IllegalArgumentException("该日市场快照尚未生成"));
        return evidence.assemble(workspace);
    }
    private MarketAiView base(MarketAiInput input) {
        MarketAiView view = new MarketAiView();
        view.setBusinessDate(input.getBusinessDate());
        view.setAsOf(input.getAsOf());
        view.setFacts(input.getFacts());
        view.setStructure(input.getStructure());
        view.setObservations(input.getObservations());
        view.setObservationSamples(input.getObservationSamples());
        view.setAdjustmentLimit(input.getAdjustmentLimit());
        view.setSectorNames(input.getSectorNames());
        view.setModel(client.model());
        view.setEffort(properties.getReasoningEffort());
        view.setStatus(!properties.isEnabled() ? MarketAiStatus.DISABLED : !client.isConfigured() ? MarketAiStatus.UNAVAILABLE : MarketAiStatus.NOT_GENERATED);
        boolean hasParticipation = input.getFacts().stream().anyMatch(f -> input.getBusinessDate().toString().equals(f.getBusinessDate())
                && "市场参与".equals(f.getLabel()) && !f.getValue().startsWith("上涨比例 缺失"));
        if (!hasParticipation || !input.getObservations().containsKey(com.finscope.common.enums.marketpulse.MarketScenarioMetric.ADVANCE_RATIO)) {
            view.setStatus(MarketAiStatus.UNAVAILABLE);
        }
        return view;
    }
    private MarketAiView reviewed(MarketAiView view, MarketAiInput input) {
        if (input.getPreviousBusinessDate() != null) {
            repository.findReviewSource(input.getPreviousBusinessDate(), input.getBusinessDate().atStartOfDay())
                    .ifPresent(previous -> evidence.review(view, input, previous));
        }
        return view;
    }
    private String key(MarketAiInput input) {
        return MarketAiAgent.VERSION + ":" + client.model() + ":" + properties.getReasoningEffort() + ":" + input.getFingerprint();
    }
    private LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
    }
}
