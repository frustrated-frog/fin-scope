package com.finscope.service.marketpulse;

import com.finscope.common.enums.marketpulse.MarketAiStatus;
import com.finscope.common.enums.marketpulse.MarketAiFailureCode;
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
        var existing = repository.find(key);
        if (retry) {
            var replayed = replay(key, input, existing.orElse(null));
            if (replayed.isPresent()) {
                return replayed.get();
            }
            repository.retryFailed(key);
            existing = repository.find(key);
        }
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

    /** 仅重放本地完整回复，绝不发送模型请求，也不改用新事实解释旧回复。 */
    public MarketAiView revalidate(LocalDate date) {
        MarketAiInput input = input(date);
        MarketAiView base = base(input);
        if (base.getStatus() != MarketAiStatus.NOT_GENERATED) {
            return base;
        }
        String key = key(input);
        var current = repository.find(key);
        return replay(key, input, current.orElse(null)).orElseGet(() -> current.orElse(base));
    }

    private java.util.Optional<MarketAiView> replay(String key, MarketAiInput input, MarketAiView current) {
        if (current == null || current.getStatus() != MarketAiStatus.FAILED && current.getStatus() != MarketAiStatus.UNKNOWN) {
            return java.util.Optional.empty();
        }
        var saved = repository.findArtifact(key);
        if (saved.isEmpty()) {
            return java.util.Optional.empty();
        }
        var artifact = saved.get();
        if (artifact.getInput() == null || !input.getFingerprint().equals(artifact.getInput().getFingerprint())
                || !artifact.isResponseCompleted() || artifact.isResponseTruncated()
                || artifact.getResponseText() == null || artifact.getRespondedAt() == null) {
            return java.util.Optional.empty();
        }
        MarketAiReport report;
        try {
            report = agent.parse(artifact.getResponseText(), artifact.getInput());
        } catch (MarketAiValidationException invalid) {
            log.info("市场AI本地重放未通过 date={} owner={} code={} path={}", input.getBusinessDate(), artifact.getOwner(), invalid.getCode(), invalid.getPath());
            return java.util.Optional.empty();
        }
        current.setReport(report);
        current.setInputTokens(artifact.getInputTokens());
        current.setOutputTokens(artifact.getOutputTokens());
        evidence.applyAssessment(current, artifact.getInput());
        current.setStatus(MarketAiStatus.READY);
        // 原始回复时间沿用到重放结果，供跨日情景校验判断是否已在当时生成。
        current.setGeneratedAt(artifact.getRespondedAt());
        current.setMessage(null);
        repository.finish(key, artifact.getOwner(), current, now());
        log.info("市场AI本地重放完成 date={} owner={} providerCalls=0", input.getBusinessDate(), artifact.getOwner());
        return java.util.Optional.of(current);
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
            repository.saveInput(key, owner, input, now());
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
            repository.saveReply(key, owner, view, reply.getText(), reply.isCompleted(), now());
            if (!reply.isCompleted()) {
                throw new MarketAiValidationException(MarketAiFailureCode.INCOMPLETE_RESPONSE, "$response.status");
            }
            if (repository.findArtifact(key).orElseThrow().isResponseTruncated()) {
                throw new MarketAiValidationException(MarketAiFailureCode.OUTPUT_TOO_LARGE, "$response.text");
            }
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
            view.setReport(null);
            view.setStructure(input.getStructure());
            view.setGeneratedAt(null);
            view.setMessage(sent ? "本次研判请求结果未确认，当前保留规则速读" : "本次研判未完成，可重试；当前保留规则速读");
            var failure = new MarketAiFailure();
            failure.setCode(error instanceof MarketAiValidationException invalid ? invalid.getCode()
                    : sent ? MarketAiFailureCode.PROVIDER_RESULT_UNKNOWN : MarketAiFailureCode.INTERNAL_ERROR);
            failure.setPath(error instanceof MarketAiValidationException invalid ? invalid.getPath() : "$request");
            failure.setErrorType(error.getClass().getSimpleName());
            // 仅记录稳定诊断字段；供应商及 Jackson 异常可能回显正文，不直接输出其异常链。
            var diagnostic = error instanceof MarketAiValidationException ? error
                    : new IllegalStateException("市场研判异常类型=" + failure.getErrorType());
            log.warn("市场AI研判未完成 date={} owner={} status={} code={} path={} errorType={}", input.getBusinessDate(), owner,
                    view.getStatus(), failure.getCode(), failure.getPath(), failure.getErrorType(), diagnostic);
            repository.finish(key, owner, view, failure, now());
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
        Double participation = input.getObservations().get(com.finscope.common.enums.marketpulse.MarketScenarioMetric.ADVANCE_RATIO);
        if (!hasParticipation || participation == null || !Double.isFinite(participation)) {
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
