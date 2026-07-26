package com.finscope.service.research.report;

import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.research.ResearchReportRepository;
import com.finscope.dao.research.ResearchThesisRepository;
import com.finscope.domain.research.ResearchReport;
import com.finscope.domain.research.ResearchSourceIdentity;
import com.finscope.domain.research.ThesisFinding;
import com.finscope.service.research.ResearchRunOutputService;
import com.finscope.service.vault.VaultWriter;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Optional;

@Service
public class ResearchReportService {
    private final RunScopedResearchContextService contextService;
    private final ResearchEvidenceSelector evidenceSelector;
    private final ResearchReportGenerator reportGenerator;
    private final ResearchReportSynthesisAgent synthesisAgent;
    private final ResearchReportRepository reportRepository;
    private final ResearchThesisRepository thesisRepository;
    private final VaultWriter vaultWriter;
    private final ResearchRunOutputService outputService;

    public ResearchReportService(RunScopedResearchContextService contextService,
                                 ResearchEvidenceSelector evidenceSelector,
                                 ResearchReportGenerator reportGenerator,
                                 ResearchReportSynthesisAgent synthesisAgent,
                                 ResearchReportRepository reportRepository,
                                 ResearchThesisRepository thesisRepository,
                                 VaultWriter vaultWriter,
                                 ResearchRunOutputService outputService) {
        this.contextService = contextService;
        this.evidenceSelector = evidenceSelector;
        this.reportGenerator = reportGenerator;
        this.synthesisAgent = synthesisAgent;
        this.reportRepository = reportRepository;
        this.thesisRepository = thesisRepository;
        this.vaultWriter = vaultWriter;
        this.outputService = outputService;
    }

    public ResearchReport generate(Long runId) {
        RunScopedResearchContext context = contextService.load(runId);
        if (context.getThesis() == null) {
            throw new IllegalStateException("A research thesis is required to generate a thesis report");
        }
        List<ResearchEvidenceCard> evidence = evidenceSelector.select(context.getThesis(), context.getArticles(),
                context.getEvidenceItems());
        if (evidence.isEmpty()) {
            throw new InsufficientResearchEvidenceException(
                    "研究运行没有可引用的有效证据，已阻止生成结论报告");
        }
        EvidenceSufficiency sufficiency = EvidenceSufficiency.assess(evidence);
        GeneratedResearchReport fallback = reportGenerator.generate(context.getThesis(), evidence, sufficiency);
        GeneratedResearchReport generated = synthesisAgent.refine(context.getThesis(), evidence, fallback);
        try {
            Path path = vaultWriter.writeResearchReport(context.getThesis().getId(), runId, generated.getMarkdown());
            ResearchReport report = new ResearchReport();
            report.setResearchRunId(runId);
            report.setThesisId(context.getThesis().getId());
            report.setReportType("THESIS");
            report.setStatus(sufficiency.isSufficient() ? "COMPLETED" : "COMPLETED_WITH_GAPS");
            report.setTitle(generated.getTitle());
            report.setConclusion(generated.getConclusion());
            report.setConclusionDirection(generated.getConclusionDirection());
            report.setConfidence(generated.getConfidence());
            report.setExecutiveSummary(generated.getExecutiveSummary());
            report.setContentMarkdown(generated.getMarkdown());
            report.setMarkdownPath(path.toString());
            report.setGenerationMode(generated.getGenerationMode());
            report.setWarningMessage(String.join("；", sufficiency.getWarnings()));
            report.setEvidenceCount(evidence.size());
            report.setSourceCount(sourceCount(evidence));
            report.setCharacterCount(generated.getMarkdown().length());
            ResearchReport saved = reportRepository.upsert(report);
            outputService.record(runId, ResearchRunOutputService.REPORT, saved.getId());
            updateThesis(context, generated, evidence);
            return saved;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to persist research report for run " + runId, ex);
        }
    }

    private void updateThesis(RunScopedResearchContext context, GeneratedResearchReport generated,
                              List<ResearchEvidenceCard> evidence) {
        context.getThesis().setConclusion(generated.getConclusion());
        context.getThesis().setConfidence(generated.getConfidence());
        context.getThesis().setNextValidation("下一披露期复核订单、资本开支、库存和产能利用率是否同向变化");
        thesisRepository.update(context.getThesis());
        thesisRepository.deleteFindingsByRunId(context.getRun().getId());
        for (ResearchEvidenceCard card : evidence) {
            ThesisFinding finding = new ThesisFinding();
            finding.setThesisId(context.getThesis().getId());
            finding.setResearchRunId(context.getRun().getId());
            finding.setStance("NEUTRAL".equals(card.getStance()) ? "UNKNOWN" : card.getStance());
            finding.setSummary(card.getClaim());
            finding.setEvidenceId(card.getEvidenceItem() == null ? null : card.getEvidenceItem().getId());
            thesisRepository.saveFinding(finding);
        }
    }

    public ResearchReport detailByRunId(Long runId) {
        return reportRepository.findByRunId(runId)
                .orElseThrow(() -> new ResourceNotFoundException("研究运行尚无研究报告：" + runId));
    }

    public Optional<ResearchReport> findByRunId(Long runId) {
        return reportRepository.findByRunId(runId);
    }

    public EvidenceSufficiency assessSufficiency(Long runId) {
        RunScopedResearchContext context = contextService.load(runId);
        if (context.getThesis() == null) {
            return EvidenceSufficiency.assess(java.util.Collections.<ResearchEvidenceCard>emptyList());
        }
        return EvidenceSufficiency.assess(evidenceSelector.select(context.getThesis(), context.getArticles(),
                context.getEvidenceItems()));
    }

    private int sourceCount(List<ResearchEvidenceCard> evidence) {
        Set<String> sources = new HashSet<String>();
        for (ResearchEvidenceCard card : evidence) {
            sources.add(ResearchSourceIdentity.resolve(card.getArticle()));
        }
        return sources.size();
    }
}
