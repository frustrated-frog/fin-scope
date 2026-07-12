package com.finscope.service.attribution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.util.StringUtils;
import com.finscope.dao.attribution.AttributionResearchRunRepository;
import com.finscope.dao.attribution.AttributionRepository;
import com.finscope.domain.attribution.AttributionDriver;
import com.finscope.domain.attribution.AttributionEvidence;
import com.finscope.domain.attribution.AttributionReport;
import com.finscope.domain.attribution.AttributionResearchRun;
import com.finscope.domain.attribution.AttributionResearchStep;
import com.finscope.domain.instrument.Instrument;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 归因研究的控制面：计划、状态、证据门和报告验收。
 * 外部搜索与模型调用仍由 AttributionAgent 执行。
 */
@Service
public class AttributionHarness {
    private final AttributionResearchPlanFactory planFactory;
    private final AttributionPlanValidator planValidator;
    private final AttributionEvidenceGate evidenceGate;
    private final AttributionResearchRunRepository runRepository;
    private final AttributionAgent agent;
    private final AttributionRepository attributionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public AttributionHarness(AttributionResearchPlanFactory planFactory,
                              AttributionPlanValidator planValidator,
                              AttributionEvidenceGate evidenceGate,
                              AttributionResearchRunRepository runRepository,
                              AttributionAgent agent,
                              AttributionRepository attributionRepository) {
        this.planFactory = planFactory;
        this.planValidator = planValidator;
        this.evidenceGate = evidenceGate;
        this.runRepository = runRepository;
        this.agent = agent;
        this.attributionRepository = attributionRepository;
    }

    /** 测试与纯控制面使用的便捷构造器。 */
    AttributionHarness(AttributionResearchPlanFactory planFactory,
                       AttributionPlanValidator planValidator,
                       AttributionEvidenceGate evidenceGate,
                       AttributionResearchRunRepository runRepository,
                       AttributionAgent agent) {
        this(planFactory, planValidator, evidenceGate, runRepository, agent, null);
    }

    public void research(AttributionReport report,
                         Instrument instrument,
                         Double changePct,
                         String taskId,
                         AttributionProgressPublisher publisher) {
        AttributionResearchPlan plan = planFactory.create(instrument, changePct);
        planValidator.validate(plan);
        AttributionResearchRun run = createRun(report.getId(), plan);
        createTrackSteps(run.getId(), plan);
        try {
            publisher.publish(taskId, AttributionProgressEvent.stage("question-plan",
                    "研究计划已通过校验，共 " + plan.getTracks().size() + " 条研究轨道"));
            AttributionResearchExecution execution = agent.researchWithPlan(
                    report, instrument, changePct, taskId, publisher, plan,
                    progressListener(run.getId(), plan));
            List<AttributionEvidence> evidences = evidenceGate.normalizeAndRank(report.getEvidences());
            report.setEvidences(evidences);
            enrichAndVerify(report, instrument, changePct, evidences);
            appendHistoricalContext(report, instrument, evidences);
            completeSteps(run.getId(), plan, execution, evidences.size());
        } catch (RuntimeException ex) {
            failUnfinishedSteps(run.getId(), "研究异常中断：" + StringUtils.firstNonBlank(ex.getMessage(), "未知错误"));
            runRepository.updateRun(run.getId(), "FAILED", ex.getMessage(), "UNHANDLED_ERROR");
            throw ex;
        }
    }

    /** 必须在报告和全部证据成功落库后调用，避免运行表与报告状态分裂。 */
    public void markPersisted(AttributionReport report) {
        runRepository.findByReportId(report.getId()).ifPresent(run -> {
            String status = StringUtils.isBlank(report.getWarningMessage()) ? "COMPLETED" : "PARTIAL";
            runRepository.updateRun(run.getId(), status, report.getWarningMessage(),
                    "COMPLETED".equals(status) ? "SUCCESS" : "DEGRADED_WITH_WARNING");
        });
    }

    public void markPersistenceFailed(AttributionReport report, Throwable error) {
        runRepository.findByReportId(report.getId()).ifPresent(run -> {
            if ("RUNNING".equals(run.getStatus())) {
                runRepository.updateRun(run.getId(), "FAILED",
                        error == null ? "报告持久化失败" : error.getMessage(), "PERSISTENCE_ERROR");
            }
        });
    }

    private void appendHistoricalContext(AttributionReport report,
                                         Instrument instrument,
                                         List<AttributionEvidence> current) {
        if (attributionRepository == null || report.getId() == null) return;
        List<AttributionEvidence> merged = new ArrayList<AttributionEvidence>(current);
        List<AttributionEvidence> historical = attributionRepository.findRecentEvidenceContext(
                instrument.getCode(), instrument.getType(), report.getId(), 4);
        for (AttributionEvidence evidence : historical) {
            evidence.setHistoricalContext(true);
            evidence.setStance("BACKGROUND");
            evidence.setDirectness("BACKGROUND");
            merged.add(evidence);
        }
        report.setEvidences(evidenceGate.normalizeAndRank(merged));
        if (!historical.isEmpty()) {
            List<String> uncertainties = new ArrayList<String>(report.getUncertainties());
            uncertainties.add("历史证据仅用于连续性对照，不作为当日高置信归因依据。");
            report.setUncertainties(uncertainties);
        }
    }

    private AttributionResearchRun createRun(Long reportId, AttributionResearchPlan plan) {
        AttributionResearchRun run = new AttributionResearchRun();
        run.setReportId(reportId);
        run.setStatus("RUNNING");
        run.setCurrentStep("research-plan");
        try {
            run.setPlanJson(objectMapper.writeValueAsString(plan));
            run.setBudgetJson(objectMapper.writeValueAsString(plan.getBudget()));
        } catch (Exception ex) {
            throw new IllegalStateException("研究计划序列化失败", ex);
        }
        return runRepository.createRun(run);
    }

    private void createTrackSteps(Long runId, AttributionResearchPlan plan) {
        for (AttributionResearchPlan.Track track : plan.getTracks()) {
            AttributionResearchStep step = new AttributionResearchStep();
            step.setRunId(runId);
            step.setStepId(track.getCode().toLowerCase());
            step.setTrack(track.getCode());
            step.setStatus("PLANNED");
            step.setInputSummary(String.join(" | ", track.getQueries()));
            step.setAttempt(0);
            step.setMaxAttempts(track.getMaxQueries());
            runRepository.saveStep(step);
        }
    }

    private void completeSteps(Long runId,
                               AttributionResearchPlan plan,
                               AttributionResearchExecution execution,
                               int evidenceCount) {
        for (AttributionResearchPlan.Track track : plan.getTracks()) {
            AttributionResearchExecution.TrackResult result = execution == null ? null : execution.get(track.getCode());
            AttributionResearchStep step = new AttributionResearchStep();
            step.setRunId(runId);
            step.setStepId(track.getCode().toLowerCase());
            step.setTrack(track.getCode());
            step.setStatus(result == null ? "SKIPPED" : result.status());
            step.setInputSummary(String.join(" | ", track.getQueries()));
            step.setOutputSummary(result == null
                    ? "未启动该轨道，归一化证据池共 " + evidenceCount + " 条"
                    : "成功查询 " + result.getSuccessfulQueries() + " 次，新增证据 " + result.getEvidenceCount() + " 条");
            step.setAttempt(result == null ? 0 : result.getAttempts());
            step.setMaxAttempts(track.getMaxQueries());
            step.setEndedAt(LocalDateTime.now());
            step.setErrorMessage(result == null ? null : result.getLastError());
            runRepository.saveStep(step);
        }
    }

    private AttributionResearchProgressListener progressListener(final Long runId,
                                                                 final AttributionResearchPlan plan) {
        return new AttributionResearchProgressListener() {
            @Override
            public void stageStarted(String stage) {
                runRepository.updateCurrentStep(runId, stage);
            }

            @Override
            public void trackStarted(AttributionResearchExecution.TrackResult result) {
                persistTrackProgress(runId, plan, result, "RUNNING", LocalDateTime.now(), null);
            }

            @Override
            public void trackUpdated(AttributionResearchExecution.TrackResult result) {
                persistTrackProgress(runId, plan, result, "RUNNING", null, null);
            }

            @Override
            public void trackFinished(AttributionResearchExecution.TrackResult result) {
                persistTrackProgress(runId, plan, result, result.status(), null, LocalDateTime.now());
            }
        };
    }

    private void persistTrackProgress(Long runId,
                                      AttributionResearchPlan plan,
                                      AttributionResearchExecution.TrackResult result,
                                      String status,
                                      LocalDateTime startedAt,
                                      LocalDateTime endedAt) {
        AttributionResearchPlan.Track track = findTrack(plan, result.getCode());
        if (track == null) return;
        AttributionResearchStep step = new AttributionResearchStep();
        step.setRunId(runId);
        step.setStepId(track.getCode().toLowerCase());
        step.setTrack(track.getCode());
        step.setStatus(status);
        step.setInputSummary(String.join(" | ", track.getQueries()));
        step.setOutputSummary("已尝试 " + result.getAttempts() + "/" + track.getMaxQueries()
                + " 次，新增证据 " + result.getEvidenceCount() + " 条");
        step.setAttempt(result.getAttempts());
        step.setMaxAttempts(track.getMaxQueries());
        step.setErrorMessage(result.getLastError());
        step.setStartedAt(startedAt);
        step.setEndedAt(endedAt);
        runRepository.saveStep(step);
    }

    private AttributionResearchPlan.Track findTrack(AttributionResearchPlan plan, String code) {
        for (AttributionResearchPlan.Track track : plan.getTracks()) {
            if (track.getCode().equals(code)) return track;
        }
        return null;
    }

    private void failUnfinishedSteps(Long runId, String message) {
        for (AttributionResearchStep step : runRepository.findStepsByRunId(runId)) {
            if ("COMPLETED".equals(step.getStatus()) || "PARTIAL".equals(step.getStatus())
                    || "FAILED".equals(step.getStatus()) || "SKIPPED".equals(step.getStatus())) {
                continue;
            }
            step.setStatus("RUNNING".equals(step.getStatus()) ? "FAILED" : "SKIPPED");
            step.setErrorMessage(message);
            step.setEndedAt(LocalDateTime.now());
            runRepository.saveStep(step);
        }
    }

    private void enrichAndVerify(AttributionReport report,
                                 Instrument instrument,
                                 Double changePct,
                                 List<AttributionEvidence> evidences) {
        List<AttributionDriver> drivers = report.getDrivers() == null
                ? new ArrayList<AttributionDriver>() : new ArrayList<AttributionDriver>(report.getDrivers());
        int target = Math.min(6, evidences.size());
        while (drivers.size() < target) {
            drivers.add(driverFromEvidence(evidences.get(drivers.size()), changePct));
        }
        for (int i = 0; i < drivers.size(); i++) {
            AttributionDriver driver = drivers.get(i);
            List<AttributionEvidence> support = supportForDriver(driver, evidences);
            AttributionEvidence evidence = support.isEmpty() && i < evidences.size() ? evidences.get(i)
                    : support.isEmpty() ? null : support.get(0);
            enrichDriver(driver, evidence, support);
        }
        report.setDrivers(drivers);
        report.setPrimaryDriver(drivers.isEmpty() ? null : drivers.get(0));
        report.setUncertainties(mergeDefaults(report.getUncertainties(), Arrays.asList(
                "公开信息只能解释价格变化的一部分，盘中资金行为和未披露信息仍可能产生影响。",
                "证据相关性不等于严格因果关系，需结合后续公告与行情验证。")));
        report.setObservationWindows(mergeDefaults(report.getObservationWindows(), Arrays.asList(
                "下一个交易日观察板块相对强弱与成交额是否延续",
                "未来一至两周关注公告、行业数据和政策兑现情况")));
        if (StringUtils.isBlank(report.getSummary())) {
            String direction = changePct != null && changePct < 0 ? "下跌" : "上涨";
            report.setSummary(StringUtils.firstNonBlank(instrument.getName(), instrument.getCode()) + "当日" + direction
                    + "由多类公开线索共同驱动，主因与次级因素详见证据链。");
        }
    }

    private AttributionDriver driverFromEvidence(AttributionEvidence evidence, Double changePct) {
        AttributionDriver driver = new AttributionDriver();
        driver.setClaim(StringUtils.firstNonBlank(evidence.getTitle(), "相关公开信息"));
        driver.setDetail(StringUtils.firstNonBlank(evidence.getSnippet(), "该线索可能改变市场预期。"));
        driver.setImpactLevel("MID");
        driver.setConfidence("T1".equals(evidence.getSourceTier()) ? "MID" : "LOW");
        driver.setEvidenceUrls(StringUtils.isBlank(evidence.getUrl())
                ? Collections.<String>emptyList() : Collections.singletonList(evidence.getUrl()));
        return driver;
    }

    private void enrichDriver(AttributionDriver driver,
                              AttributionEvidence evidence,
                              List<AttributionEvidence> support) {
        String fact = evidence == null ? driver.getDetail() : StringUtils.firstNonBlank(evidence.getSnippet(), evidence.getTitle());
        driver.setFacts(mergeDefaults(driver.getFacts(), Collections.singletonList(
                StringUtils.firstNonBlank(fact, "当前证据仅能提供方向性支持"))));
        if (StringUtils.isBlank(driver.getTransmissionPath())) {
            driver.setTransmissionPath("公开事件改变市场预期 → 影响行业/公司盈利或风险判断 → 引发资金重新定价 → 反映到当日价格");
        }
        if (StringUtils.isBlank(driver.getCounterEvidence())) {
            driver.setCounterEvidence("尚缺少逐笔资金与更多独立来源，当前解释仍需后续行情验证。");
        }
        if (StringUtils.isBlank(driver.getObservationWindow())) {
            driver.setObservationWindow("后续 1–5 个交易日观察量价和相关公告是否确认");
        }
        driver.setConfidence(evidenceGate.capConfidence(StringUtils.firstNonBlank(driver.getConfidence(), "LOW"),
                support == null ? Collections.<AttributionEvidence>emptyList() : support));
    }

    private List<AttributionEvidence> supportForDriver(AttributionDriver driver, List<AttributionEvidence> evidences) {
        List<AttributionEvidence> result = new ArrayList<AttributionEvidence>();
        if (driver.getEvidenceUrls() == null || driver.getEvidenceUrls().isEmpty()) return result;
        for (AttributionEvidence evidence : evidences) {
            if (StringUtils.isNotBlank(evidence.getUrl()) && driver.getEvidenceUrls().contains(evidence.getUrl())) {
                result.add(evidence);
            }
        }
        return result;
    }

    private List<String> mergeDefaults(List<String> values, List<String> defaults) {
        List<String> merged = new ArrayList<String>();
        if (values != null) {
            for (String value : values) if (StringUtils.isNotBlank(value) && !merged.contains(value)) merged.add(value);
        }
        for (String value : defaults) if (!merged.contains(value)) merged.add(value);
        return merged;
    }
}
