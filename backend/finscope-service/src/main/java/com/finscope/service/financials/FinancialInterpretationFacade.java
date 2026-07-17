package com.finscope.service.financials;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.financials.FinancialAnalysisSnapshotRepository;
import com.finscope.dao.financials.FinancialInterpretationRepository;
import com.finscope.domain.agent.AgentActionFingerprint;
import com.finscope.domain.agent.AgentNodeResult;
import com.finscope.domain.agent.AgentRunContext;
import com.finscope.domain.agent.AgentTraceSubject;
import com.finscope.domain.financials.FinancialAnalysisSnapshot;
import com.finscope.domain.financials.FinancialEvidence;
import com.finscope.domain.financials.FinancialInterpretation;
import com.finscope.domain.financials.FinancialReport;
import com.finscope.domain.financials.FinancialReportView;
import com.finscope.service.agent.AgentHarness;
import com.finscope.service.agent.AgentTraceService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;

@Service
public class FinancialInterpretationFacade {
    private final FinancialQueryService query;
    private final FinancialAnalysisSnapshotRepository snapshots;
    private final FinancialInterpretationRepository interpretations;
    private final FinancialAnalysisPreflight preflight;
    private final FinancialEvidencePacketAssembler assembler;
    private final FinancialInterpretationAgent agent;
    private final AgentHarness harness;
    private final AgentTraceService traces;
    private final ObjectMapper json;
    private final Executor executor;

    public FinancialInterpretationFacade(
            FinancialQueryService query,
            FinancialAnalysisSnapshotRepository snapshots,
            FinancialInterpretationRepository interpretations,
            FinancialAnalysisPreflight preflight,
            FinancialEvidencePacketAssembler assembler,
            FinancialInterpretationAgent agent,
            AgentHarness harness,
            AgentTraceService traces,
            ObjectMapper json,
            @Qualifier("financialInterpretationExecutor") Executor executor) {
        this.query = query;
        this.snapshots = snapshots;
        this.interpretations = interpretations;
        this.preflight = preflight;
        this.assembler = assembler;
        this.agent = agent;
        this.harness = harness;
        this.traces = traces;
        this.json = json;
        this.executor = executor;
    }

    public synchronized FinancialInterpretation request(Long reportId, boolean force) {
        FinancialReportView current = query.view(reportId);
        List<FinancialReportView> comparables = comparables(current);
        current = preflight.ensureCurrent(current, comparables);
        FinancialEvidencePacket packet = assembler.assemble(current, comparables);
        FinancialAnalysisSnapshot snapshot = new FinancialAnalysisSnapshot();
        snapshot.setReportId(reportId);
        snapshot.setAlgorithmVersion(packet.getAlgorithmVersion());
        snapshot.setSourceHash(packet.getSourceHash());
        snapshot.setInputHash(packet.getInputHash());
        snapshot.setPayloadJson(packet.getPayloadJson());
        snapshot.setQualityLevel(packet.getQualityCeiling());
        snapshots.saveOrReuse(snapshot);

        Optional<FinancialInterpretation> running = interpretations.findRunningByReport(reportId);
        if (running.isPresent()) return markStale(running.get());
        String generationKey = sha256(packet.getInputHash() + "|" + packet.getPromptVersion()
                + "|" + agent.modelName());
        if (!force) {
            Optional<FinancialInterpretation> reusable = interpretations.findReusable(generationKey);
            if (reusable.isPresent()) return markStale(reusable.get());
        }
        FinancialInterpretation pending = new FinancialInterpretation();
        pending.setReportId(reportId);
        pending.setSnapshotId(snapshot.getId());
        pending.setGenerationKey(generationKey);
        pending.setPromptVersion(packet.getPromptVersion());
        pending.setModelName(agent.modelName());
        pending.setStatus("QUEUED");
        interpretations.save(pending);
        try {
            executor.execute(() -> complete(pending, packet));
        } catch (RuntimeException error) {
            pending.setStatus("FAILED");
            pending.setFailureCode("EXECUTOR_REJECTED");
            pending.setFailureMessage(error.getMessage());
            pending.setCompletedAt(LocalDateTime.now());
            interpretations.update(pending);
        }
        return pending;
    }

    public FinancialInterpretation latest(Long reportId) {
        query.view(reportId);
        Optional<FinancialInterpretation> running = interpretations.findRunningByReport(reportId);
        if (running.isPresent()) return markStale(running.get());
        return markStale(interpretations.findLatestDisplayable(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("该报告尚无财报解读：" + reportId)));
    }

    public FinancialInterpretation get(Long id) {
        return markStale(interpretations.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("财报解读不存在：" + id)));
    }

    public List<FinancialInterpretation> history(Long reportId, int limit) {
        query.view(reportId);
        List<FinancialInterpretation> values = interpretations.findHistory(reportId, limit);
        values.forEach(this::markStale);
        return values;
    }

    public List<FinancialEvidence> evidence(Long interpretationId) {
        try {
            FinancialInterpretation interpretation = get(interpretationId);
            FinancialAnalysisSnapshot snapshot = snapshots.findById(interpretation.getSnapshotId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "财报分析快照不存在：" + interpretation.getSnapshotId()));
            JsonNode evidenceNode = json.readTree(snapshot.getPayloadJson()).path("evidence");
            List<FinancialEvidence> all = json.convertValue(evidenceNode,
                    new TypeReference<List<FinancialEvidence>>() { });
            Set<String> used = usedRefs(interpretation.getResult());
            if (used.isEmpty()) return new ArrayList<FinancialEvidence>();
            List<FinancialEvidence> result = new ArrayList<FinancialEvidence>();
            for (FinancialEvidence item : all) {
                if (used.contains(item.getId())) result.add(item);
            }
            return result;
        } catch (ResourceNotFoundException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("cannot read financial interpretation evidence", error);
        }
    }

    private void complete(FinancialInterpretation pending, FinancialEvidencePacket packet) {
        long started = System.nanoTime();
        pending.setStatus("RUNNING");
        pending.setStartedAt(LocalDateTime.now());
        interpretations.update(pending);
        AgentRunContext context = AgentRunContext.start(null, null);
        AgentActionFingerprint fingerprint = AgentActionFingerprint.of(
                "financial-interpret", "FINANCIAL_SNAPSHOT", String.valueOf(pending.getSnapshotId()),
                "financial-interpret:" + pending.getGenerationKey(), packet.getInputHash());
        AgentNodeResult<FinancialInterpretation> node = harness.runNode(context, fingerprint, actual -> {
            actual.recordLlmCall();
            FinancialInterpretation output = agent.interpret(packet);
            return AgentNodeResult.success(output, "snapshot=" + pending.getSnapshotId(),
                    "status=" + output.getStatus(), 1);
        });
        pending.setStatus("VALIDATING");
        interpretations.update(pending);
        FinancialInterpretation output = node.getValue();
        if (output == null) {
            pending.setStatus("FAILED");
            pending.setFailureCode(node.getErrorType());
            pending.setFailureMessage(node.getErrorMessage());
        } else {
            pending.setStatus(output.getStatus());
            pending.setGenerationMode(output.getGenerationMode());
            pending.setResult(output.getResult());
            pending.setValidationErrors(output.getValidationErrors());
            pending.setFailureCode(output.getFailureCode());
            pending.setFailureMessage(output.getFailureMessage());
            pending.setModelName(output.getModelName());
        }
        pending.setDurationMs((System.nanoTime() - started) / 1_000_000L);
        pending.setCompletedAt(LocalDateTime.now());
        interpretations.update(pending);
        traces.recordNode(AgentTraceSubject.of("FINANCIAL_INTERPRETATION", pending.getId()),
                context, fingerprint, node, pending.getDurationMs(),
                "{\"snapshotId\":" + pending.getSnapshotId() + "}");
    }

    private List<FinancialReportView> comparables(FinancialReportView current) {
        List<FinancialReportView> result = new ArrayList<FinancialReportView>();
        for (FinancialReport report : query.listReports(current.getInstrument().getId())) {
            if (report.getId().equals(current.getReport().getId())) continue;
            result.add(query.view(report.getId()));
            if (result.size() >= 8) break;
        }
        return result;
    }

    private FinancialInterpretation markStale(FinancialInterpretation value) {
        Optional<FinancialAnalysisSnapshot> latest = snapshots.findLatest(value.getReportId());
        value.setSnapshotStale(latest.isPresent()
                && !latest.get().getId().equals(value.getSnapshotId()));
        return value;
    }

    private Set<String> usedRefs(FinancialInterpretation.Result result) {
        Set<String> refs = new LinkedHashSet<String>();
        if (result == null) return refs;
        addClaimRefs(refs, result.getExecutiveSummary());
        addClaimRefs(refs, result.getPositiveSignals());
        addClaimRefs(refs, result.getRisks());
        addClaimRefs(refs, result.getTurningPoints());
        addClaimRefs(refs, result.getWatchpoints());
        if (result.getDimensions() != null) {
            result.getDimensions().forEach(value -> refs.addAll(value.getRefs()));
        }
        return refs;
    }

    private void addClaimRefs(Set<String> refs, List<FinancialInterpretation.Claim> values) {
        if (values != null) values.forEach(value -> refs.addAll(value.getRefs()));
    }

    private String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : bytes) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}
