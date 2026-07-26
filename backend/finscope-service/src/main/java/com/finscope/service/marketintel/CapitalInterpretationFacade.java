package com.finscope.service.marketintel;

import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.marketintel.CapitalBehaviorEvaluationRepository;
import com.finscope.dao.marketintel.CapitalBehaviorSnapshotRepository;
import com.finscope.dao.marketintel.CapitalInterpretationRepository;
import com.finscope.domain.agent.AgentActionFingerprint;
import com.finscope.domain.agent.AgentNodeResult;
import com.finscope.domain.agent.AgentRunContext;
import com.finscope.domain.agent.AgentTraceSubject;
import com.finscope.domain.marketintel.CapitalBehaviorSnapshot;
import com.finscope.domain.marketintel.CapitalAgentEvidencePacket;
import com.finscope.domain.marketintel.CapitalInterpretation;
import com.finscope.domain.marketintel.CapitalRuleExplanation;
import com.finscope.rpc.marketintel.JdkFinanceHttpClient;
import com.finscope.service.agent.AgentHarness;
import com.finscope.service.agent.AgentTraceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class CapitalInterpretationFacade {
    private final CapitalBehaviorSnapshotRepository snapshots;
    private final CapitalBehaviorEvaluationRepository evaluations;
    private final CapitalInterpretationRepository interpretations;
    private final CapitalRuleExplanationService rules;
    private final CapitalInterpretationAgent agent;
    private final CapitalFactAssembler facts;
    private final CapitalAgentEvidenceAssembler evidenceAssembler;
    private final AgentHarness harness;
    private final AgentTraceService traces;
    @Resource(name = "marketIntelAgentExecutor")
    private Executor executor;

    public CapitalInterpretationFacade(CapitalBehaviorSnapshotRepository snapshots,
                                       CapitalBehaviorEvaluationRepository evaluations,
                                       CapitalInterpretationRepository interpretations,
                                       CapitalRuleExplanationService rules,
                                       CapitalInterpretationAgent agent,
                                       CapitalFactAssembler facts,
                                       CapitalAgentEvidenceAssembler evidenceAssembler,
                                       AgentHarness harness, AgentTraceService traces) {
        this.snapshots = snapshots;
        this.evaluations = evaluations;
        this.interpretations = interpretations;
        this.rules = rules;
        this.agent = agent;
        this.facts = facts;
        this.evidenceAssembler = evidenceAssembler;
        this.harness = harness;
        this.traces = traces;
    }

    public synchronized CapitalInterpretation request(Long instrumentId, boolean force) {
        CapitalBehaviorSnapshot snapshot = snapshots.findLatest(instrumentId)
                .orElseThrow(() -> new ResourceNotFoundException("标的尚无资金行为快照：" + instrumentId));
        CapitalRuleExplanation explanation = rules.explain(snapshot.getFacts(), snapshot.getSignals());
        CapitalAgentEvidencePacket packet = evidenceAssembler.assemble(snapshot, explanation,
                evaluations.findBySnapshotId(snapshot.getId()).orElse(null));
        String base = packet.getEvidenceFingerprint();
        String inputHash = force ? JdkFinanceHttpClient.sha256(base + "|" + System.nanoTime()) : base;
        java.util.Optional<CapitalInterpretation> running = interpretations
                .findRunningByInstrumentAndSnapshot(instrumentId, snapshot.getId());
        if (running.isPresent()) return running.get();
        if (!force) {
            java.util.Optional<CapitalInterpretation> cached = interpretations.findByAction(snapshot.getId(), "AGENT", inputHash);
            if (cached.isPresent()) return cached.get();
        }
        CapitalInterpretation pending = new CapitalInterpretation();
        pending.setInstrumentId(instrumentId);
        pending.setSnapshotId(snapshot.getId());
        pending.setInterpretationType("AGENT");
        pending.setStatus("PENDING");
        pending.setPlainSummary("Agent 正在基于已保存事实进行解读");
        pending.setFacts(facts.assemble(snapshot));
        pending.setHypotheses(Collections.emptyList());
        pending.setDataGaps(explanation.getDataGaps());
        pending.setObservationPoints(Collections.emptyList());
        pending.setDisclaimer("模型解读仅用于研究，不构成投资建议。");
        pending.setRuleVersion(explanation.getRuleVersion());
        pending.setPromptVersion(packet.getPromptVersion());
        pending.setFactorVersion(packet.getFactorVersion());
        pending.setSignalVersion(packet.getSignalVersion());
        pending.setEvidenceRefs(packet.getRawMetrics());
        pending.setWatchConditionRefs(packet.getWatchConditions().stream()
                .map(item -> item.getId()).collect(java.util.stream.Collectors.toList()));
        pending.setInputHash(inputHash);
        interpretations.save(pending);
        try {
            executor.execute(() -> complete(pending, snapshot, packet, explanation, inputHash));
        } catch (RuntimeException e) {
            pending.setStatus("FAILED");
            pending.setFallbackReason("EXECUTOR_REJECTED");
            pending.setPlainSummary("Agent 任务队列暂时不可用，请稍后重试");
            interpretations.update(pending);
        }
        return pending;
    }

    public CapitalInterpretation interpret(Long instrumentId, boolean force) {
        return request(instrumentId, force);
    }

    public CapitalInterpretation latest(Long instrumentId) {
        CapitalBehaviorSnapshot snapshot = snapshots.findLatest(instrumentId)
                .orElseThrow(() -> new ResourceNotFoundException("标的尚无资金行为快照：" + instrumentId));
        return interpretations.findLatestByInstrumentAndSnapshot(instrumentId, snapshot.getId())
                .orElseThrow(() -> new ResourceNotFoundException("该标的当前快照尚无资金行为解读：" + instrumentId));
    }

    public CapitalInterpretation get(Long interpretationId) {
        return interpretations.findById(interpretationId)
                .orElseThrow(() -> new ResourceNotFoundException("资金行为解读不存在：" + interpretationId));
    }

    private void complete(CapitalInterpretation pending, CapitalBehaviorSnapshot snapshot,
                          CapitalAgentEvidencePacket packet, CapitalRuleExplanation explanation,
                          String inputHash) {
        AgentRunContext context = AgentRunContext.start(null, null);
        AgentActionFingerprint fp = AgentActionFingerprint.of("capital-interpret", "CAPITAL_SNAPSHOT", String.valueOf(snapshot.getId()), "capital-interpret:" + inputHash, inputHash);
        long started = System.nanoTime();
        AgentNodeResult<CapitalInterpretation> node;
        try {
            node = harness.runNode(context, fp, ctx -> {
                CapitalInterpretationAgent.Execution execution = agent.interpretWithMetrics(packet, explanation);
                ctx.recordLlmCalls(execution.getLlmCallCount());
                CapitalInterpretation value = execution.getValue();
                if ("FALLBACK".equals(value.getStatus()) || "INSUFFICIENT_DATA".equals(value.getStatus())) {
                    return AgentNodeResult.fallback(value, "snapshot=" + snapshot.getId(),
                            "status=" + value.getStatus(), value.getFallbackReason(), 1);
                }
                return AgentNodeResult.success(value, "snapshot=" + snapshot.getId(),
                        "status=" + value.getStatus(), 1);
            });
            CapitalInterpretation value = node.getValue();
            if (value == null) {
                pending.setStatus("FAILED");
                pending.setFallbackReason(node.getErrorType());
                pending.setPlainSummary(explanation.getSummary());
            } else {
                copyResult(pending, value);
            }
            interpretations.update(pending);
        } catch (RuntimeException error) {
            node = AgentNodeResult.failed("UNEXPECTED_RUNTIME_ERROR", safeMessage(error));
            pending.setStatus("FAILED");
            pending.setFallbackReason("UNEXPECTED_RUNTIME_ERROR");
            pending.setPlainSummary("Agent 解读执行异常，请重新运行");
            try {
                interpretations.update(pending);
            } catch (RuntimeException updateError) {
                log.error("资金 Agent 失败状态持久化失败 interpretationId={}", pending.getId(), updateError);
            }
        }
        try {
            traces.recordNode(AgentTraceSubject.of("CAPITAL_INTERPRETATION", pending.getId()), context, fp, node,
                    (System.nanoTime() - started) / 1000000, "{\"snapshotId\":" + snapshot.getId() + "}");
        } catch (RuntimeException traceError) {
            log.warn("资金 Agent Trace 写入失败 interpretationId={}", pending.getId(), traceError);
        }
    }

    private void copyResult(CapitalInterpretation pending, CapitalInterpretation value) {
        pending.setStatus(value.getStatus());
        pending.setPlainSummary(value.getPlainSummary());
        pending.setFacts(value.getFacts());
        pending.setHypotheses(value.getHypotheses());
        pending.setDataGaps(value.getDataGaps());
        pending.setObservationPoints(value.getObservationPoints());
        pending.setDisclaimer(value.getDisclaimer());
        pending.setFallbackReason(value.getFallbackReason());
        pending.setModelName(value.getModelName());
        pending.setPromptVersion(value.getPromptVersion());
        pending.setOutputHash(value.getOutputHash());
        pending.setMarketState(value.getMarketState());
        pending.setExecutiveSummary(value.getExecutiveSummary());
        pending.setObservations(value.getObservations());
        pending.setCounterEvidence(value.getCounterEvidence());
        pending.setWatchConditionRefs(value.getWatchConditionRefs());
        pending.setConfidence(value.getConfidence());
        pending.setFactorVersion(value.getFactorVersion());
        pending.setSignalVersion(value.getSignalVersion());
        pending.setEvidenceRefs(value.getEvidenceRefs());
        pending.setRejectedOutputCount(value.getRejectedOutputCount());
        pending.setRejectionReasons(value.getRejectionReasons());
    }

    private String safeMessage(Throwable error) {
        if (error == null) return "unknown error";
        String value = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
