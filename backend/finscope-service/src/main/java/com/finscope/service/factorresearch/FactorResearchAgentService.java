package com.finscope.service.factorresearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.dao.factorresearch.FactorResearchAgentRunRepository;
import com.finscope.domain.agent.AgentRun;
import com.finscope.domain.factorresearch.FactorIdentity;
import com.finscope.domain.factorresearch.FactorResearchAgentRun;
import com.finscope.domain.factorresearch.ResearchFactorDefinition;
import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.domain.quant.factor.FactorAnalysis;
import com.finscope.service.quant.data.QuantDatasetService;
import com.finscope.service.quant.factor.DatasetFactorAnalysisService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Read-only, explicitly approved agent that can only execute a fixed research plan. */
@Service
public class FactorResearchAgentService {
    public static final String SUBJECT_TYPE = "FACTOR_RESEARCH_RUN";
    private final FactorResearchAgentRunRepository runs;
    private final AgentRunRepository traces;
    private final QuantDatasetService datasets;
    private final DatasetFactorAnalysisService diagnostics;
    private final ResearchFactorCatalog catalog;
    private final ResearchDraftService drafts;
    private final ObjectMapper json;
    private final Clock clock;

    public FactorResearchAgentService(FactorResearchAgentRunRepository runs, AgentRunRepository traces,
                                      QuantDatasetService datasets, DatasetFactorAnalysisService diagnostics,
                                      ResearchFactorCatalog catalog, ResearchDraftService drafts, ObjectMapper json) {
        this(runs, traces, datasets, diagnostics, catalog, drafts, json, Clock.systemDefaultZone());
    }

    FactorResearchAgentService(FactorResearchAgentRunRepository runs, AgentRunRepository traces,
                               QuantDatasetService datasets, DatasetFactorAnalysisService diagnostics,
                               ResearchFactorCatalog catalog, ResearchDraftService drafts, ObjectMapper json, Clock clock) {
        this.runs = runs; this.traces = traces; this.datasets = datasets; this.diagnostics = diagnostics;
        this.catalog = catalog; this.drafts = drafts; this.json = json; this.clock = clock;
    }

    public FactorResearchAgentRun createPlan(Long datasetId, FactorIdentity factor, Long draftId, String question) {
        if (datasetId == null || factor == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "数据集和因子不能为空");
        QuantDataset dataset = datasets.get(datasetId);
        catalog.get(factor.getNamespace(), factor.getCode(), factor.getVersion());
        if (draftId != null) drafts.get(draftId);
        FactorResearchAgentRun run = new FactorResearchAgentRun();
        run.setDatasetId(datasetId); run.setDatasetFingerprint(dataset.getFingerprint() == null ? "" : dataset.getFingerprint());
        run.setFactor(factor); run.setResearchDraftId(draftId);
        run.setQuestion(question == null || question.trim().isEmpty() ? "这项因子在当前数据集中的证据是否支持预设研究方向？" : question.trim());
        run.setStatus("AWAITING_APPROVAL");
        List<String> plan = new ArrayList<String>();
        if (draftId != null) plan.add("检查资金行为研究草稿的来源边界");
        plan.add("检查数据集质量、指纹与因子可用性"); plan.add("核对因子版本、公式和解释边界");
        plan.add("运行确定性横截面诊断"); plan.add("强制审查反证、样本限制和下一步");
        run.setPlan(plan);
        run.setAllowedTools(draftId == null
                ? Arrays.asList("inspect_dataset", "inspect_factor_definition", "run_factor_diagnostics")
                : Arrays.asList("inspect_research_draft", "inspect_dataset", "inspect_factor_definition", "run_factor_diagnostics"));
        run.setMaxToolCalls(4); run.setMaxLlmCalls(0); run.setMaxRunSeconds(60); run.setCreatedAt(LocalDateTime.now(clock));
        return runs.save(run);
    }

    public FactorResearchAgentRun approveAndRun(Long id) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (!runs.transition(id, "AWAITING_APPROVAL", "APPROVED", now)) {
            throw new BusinessException(ErrorCode.CONFLICT, "研究计划已批准、已运行或状态已变化");
        }
        if (!runs.transition(id, "APPROVED", "RUNNING", now)) {
            throw new BusinessException(ErrorCode.CONFLICT, "研究 Agent 无法进入运行状态");
        }
        FactorResearchAgentRun run = require(id); int calls = 0;
        try {
            ObjectNode evidence = json.createObjectNode();
            if (run.getResearchDraftId() != null) {
                enforceBudget(run, ++calls); evidence.set("researchDraft", json.valueToTree(drafts.get(run.getResearchDraftId())));
                trace(run, "inspect_research_draft", "研究草稿 " + run.getResearchDraftId(), "来源边界已读取", calls);
            }
            enforceBudget(run, ++calls); QuantDataset dataset = datasets.get(run.getDatasetId());
            ObjectNode datasetEvidence = json.createObjectNode();
            datasetEvidence.put("id", dataset.getId()); datasetEvidence.put("status", dataset.getStatus());
            datasetEvidence.put("dataKind", dataset.getDataKind()); datasetEvidence.put("datasetLevel", dataset.getDatasetLevel());
            datasetEvidence.put("fingerprint", dataset.getFingerprint());
            datasetEvidence.set("availableFactors", json.valueToTree(datasets.availableFactorCodes(run.getDatasetId())));
            evidence.set("dataset", datasetEvidence);
            trace(run, "inspect_dataset", "数据集 " + run.getDatasetId(), "质量、指纹和可用因子已读取", calls);

            enforceBudget(run, ++calls); ResearchFactorDefinition definition = catalog.get(run.getFactor().getNamespace(), run.getFactor().getCode(), run.getFactor().getVersion());
            evidence.set("factorDefinition", json.valueToTree(definition));
            trace(run, "inspect_factor_definition", run.getFactor().toString(), "公式、版本与解释边界已核对", calls);

            enforceBudget(run, ++calls); FactorAnalysis analysis = diagnostics.analyze(run.getDatasetId(), run.getFactor().getCode());
            evidence.set("diagnostics", json.valueToTree(analysis));
            trace(run, "run_factor_diagnostics", "只读横截面诊断", "确定性评价门禁已完成：" + analysis.getConclusion(), calls);

            ObjectNode finding = json.createObjectNode(); finding.put("verdict", analysis.getConclusion());
            finding.put("summary", summary(analysis)); finding.set("counterEvidence", json.valueToTree(analysis.getCaveats()));
            finding.set("blockingReasons", json.valueToTree(analysis.getBlockingReasons()));
            finding.set("nextSteps", json.valueToTree(Arrays.asList("在独立样本和不同市场阶段复核", "执行成本与容量压力测试", "通过人工评审后再考虑生命周期升级")));
            String evidenceJson = json.writeValueAsString(evidence); String findingJson = json.writeValueAsString(finding);
            trace(run, "review_counter_evidence", "确定性证据包", "反证和停止条件已强制写入", calls);
            runs.complete(id, "COMPLETED", calls, evidenceJson, sha256(evidenceJson), findingJson,
                    analysis.isValidationEligible() ? "POLICY_REVIEW_COMPLETE" : "EVIDENCE_GATE_BLOCKED", LocalDateTime.now(clock));
        } catch (Exception ex) {
            runs.complete(id, "FAILED", calls, "{}", "", "{}", safe(ex.getMessage()), LocalDateTime.now(clock));
            if (ex instanceof BusinessException) throw (BusinessException) ex;
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "研究 Agent 运行失败", ex);
        }
        return get(id);
    }

    public FactorResearchAgentRun get(Long id) {
        FactorResearchAgentRun value = require(id);
        value.setTrace(traces.findBySubject(SUBJECT_TYPE, id)); return value;
    }

    private FactorResearchAgentRun require(Long id) { return runs.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "研究 Agent 运行不存在")); }
    private void enforceBudget(FactorResearchAgentRun run, int used) { if (used > run.getMaxToolCalls()) throw new BusinessException(ErrorCode.CONFLICT, "TOOL_BUDGET_EXHAUSTED"); }
    private String summary(FactorAnalysis a) { return "结论为" + a.getConclusion() + "；有效日度 IC " + a.getSampleCount() + " 个，方向对齐 IC " + String.format("%.3f", a.getDirectionAdjustedIcMean()) + "。"; }
    private String safe(String value) { return value == null ? "UNKNOWN_FAILURE" : value.substring(0, Math.min(300, value.length())); }
    private String sha256(String value) throws Exception { byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder out = new StringBuilder(); for (byte b : bytes) out.append(String.format("%02x", b)); return out.toString(); }

    private void trace(FactorResearchAgentRun run, String node, String input, String output, int calls) {
        AgentRun trace = new AgentRun(); trace.setSubjectType(SUBJECT_TYPE); trace.setSubjectId(run.getId()); trace.setNodeName(node);
        trace.setStatus("SUCCESS"); trace.setInput(input); trace.setOutput(output); trace.setErrorMessage(""); trace.setDurationMs(0);
        trace.setStepId(node); trace.setAttempt(1); trace.setActionFingerprint(node + ":" + run.getId()); trace.setInputHash(""); trace.setOutputHash("");
        trace.setErrorType(""); trace.setFallbackUsed(false); trace.setFallbackReason(""); trace.setTerminationReason(""); trace.setProgressDelta(1);
        trace.setBudgetSnapshot("{\"toolCallsUsed\":" + calls + ",\"maxToolCalls\":" + run.getMaxToolCalls() + ",\"llmCallsUsed\":0}");
        trace.setMetadataJson("{\"readOnly\":true}"); traces.record(trace);
    }
}
