package com.finscope.service.research.report;

import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.research.ResearchReportRepository;
import com.finscope.dao.research.ResearchSearchEvidenceRepository;
import com.finscope.dao.research.ResearchThesisRepository;
import com.finscope.domain.research.ResearchReport;
import com.finscope.domain.research.ThesisFinding;
import com.finscope.domain.article.Article;
import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.ResearchThesis;
import com.finscope.domain.research.ResearchSearchEvidence;
import com.finscope.domain.research.quality.*;
import com.finscope.service.research.quality.ResearchQualityService;
import com.finscope.service.research.quality.ResearchQualityReportRenderer;
import com.finscope.service.research.ResearchRunOutputService;
import com.finscope.service.vault.VaultWriter;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import javax.annotation.Resource;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.ArrayList;

@Service
public class ResearchReportService {
    @Resource
    private RunScopedResearchContextService contextService;
    @Resource
    private ResearchEvidenceSelector evidenceSelector;
    @Resource
    private ResearchReportGenerator reportGenerator;
    @Resource
    private ResearchReportSynthesisAgent synthesisAgent;
    @Resource
    private ResearchReportRepository reportRepository;
    @Resource
    private ResearchThesisRepository thesisRepository;
    @Resource
    private ResearchSearchEvidenceRepository searchEvidenceRepository;
    @Resource
    private VaultWriter vaultWriter;
    @Resource
    private ResearchRunOutputService outputService;

    @Resource
    private ResearchArchivedEventSource archivedEventSource;
    @Resource
    private ResearchQualityService qualityService;
    @Resource
    private ResearchQualityReportRenderer qualityRenderer;

    public ResearchReport generate(Long runId) {
        RunScopedResearchContext context = contextService.load(runId);
        if (context.getThesis() == null) {
            throw new IllegalStateException("A research thesis is required to generate a thesis report");
        }
        ResearchThesis taskThesis = qualityService.taskThesis(context.getRun(), context.getThesis());
        List<Article> articles = new ArrayList<>(context.getArticles());
        articles.addAll(archivedEventSource.archivedArticlesFor(context.getRun(), taskThesis));
        List<ResearchSearchEvidence> search = searchEvidenceRepository.findByRunId(runId);
        List<ResearchEvidenceCard> selected = evidenceSelector.select(taskThesis, articles,
                context.getEvidenceItems(), search);
        ResearchQualityAssessment quality = qualityService.assess(context.getRun(), taskThesis, articles,
                search, selected, true);
        List<ResearchEvidenceCard> evidence = qualityEvidence(selected, quality);
        if (selected.isEmpty()) {
            throw new InsufficientResearchEvidenceException(
                    "研究运行没有可引用的有效证据，已阻止生成结论报告");
        }
        EvidenceSufficiency sufficiency = EvidenceSufficiency.assess(evidence).withQuality(quality);
        GeneratedResearchReport fallback = reportGenerator.generate(taskThesis, evidence, sufficiency);
        GeneratedResearchReport narrative = sufficiency.isSufficient()
                ? synthesisAgent.refine(taskThesis, evidence, fallback) : fallback;
        String boundary = quality.complete() ? "" : "当前研究尚有未解决的任务事实、日期、来源或数值缺口，以下结论仅为阶段性判断。";
        GeneratedResearchReport generated = new GeneratedResearchReport(narrative.getTitle(),
                boundary + narrative.getConclusion(), narrative.getConclusionDirection(),
                sufficiency.isSufficient() ? narrative.getConfidence() : "LOW",
                boundary + narrative.getExecutiveSummary(), narrative.getMarkdown() + qualityRenderer.render(quality),
                narrative.getGenerationMode(), narrative.getWarning());
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
            List<String> warnings = new ArrayList<String>(sufficiency.getWarnings());
            if (generated.getWarning() != null && !generated.getWarning().trim().isEmpty()) {
                warnings.add(generated.getWarning());
            }
            report.setWarningMessage(String.join("；", warnings));
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
        ResearchThesis taskThesis = qualityService.taskThesis(context.getRun(), context.getThesis());
        List<Article> articles = new ArrayList<>(context.getArticles());
        articles.addAll(archivedEventSource.archivedArticlesFor(context.getRun(), taskThesis));
        List<ResearchSearchEvidence> search = searchEvidenceRepository.findByRunId(runId);
        List<ResearchEvidenceCard> selected = evidenceSelector.select(taskThesis, articles,
                context.getEvidenceItems(), search);
        ResearchQualityAssessment quality = qualityService.assess(context.getRun(), taskThesis, articles,
                search, selected, false);
        return EvidenceSufficiency.assess(qualityEvidence(selected, quality)).withQuality(quality);
    }

    public ResearchTaskContract initializeContract(ResearchRun run, ResearchThesis thesis) {
        return qualityService.initialize(run, thesis);
    }

    public Optional<ResearchQualityAssessment> findQuality(Long runId) {
        return qualityService.findLatest(runId);
    }

    private List<ResearchEvidenceCard> qualityEvidence(List<ResearchEvidenceCard> selected,
                                                       ResearchQualityAssessment quality) {
        List<ResearchEvidenceCard> accepted = new ArrayList<>();
        for (ResearchEvidenceCard card : selected) {
            ResearchEvidenceReview review = quality.getEvidence().stream()
                    .filter(item -> java.util.Objects.equals(item.getUrl(), card.getArticle().getUrl()) && item.isAccepted())
                    .findFirst().orElse(null);
            if (review == null) {
                continue;
            }
            String quotes = quality.getFacts().stream()
                    .filter(fact -> fact.isVerified() && review.getEvidenceRef().equals(fact.getEvidenceRef()))
                    .map(StructuredResearchFact::getQuote).distinct().collect(java.util.stream.Collectors.joining("\n"));
            Article original = card.getArticle();
            Article clean = new Article();
            clean.setId(original.getId());
            clean.setTitle(original.getTitle());
            clean.setUrl(original.getUrl());
            clean.setSourceName(original.getSourceName());
            clean.setPublishedAt(original.getPublishedAt());
            clean.setSummary(quotes);
            clean.setBody(quotes);
            accepted.add(new ResearchEvidenceCard(clean, card.getEvidenceItem(), card.getStance(),
                    card.getRelevanceScore(), quotes, review.getSourceGroup(), card.getSourceTier()));
        }
        return accepted;
    }

    private int sourceCount(List<ResearchEvidenceCard> evidence) {
        Set<String> sources = new HashSet<String>();
        for (ResearchEvidenceCard card : evidence) {
            sources.add(card.getSourceIdentity());
        }
        return sources.size();
    }
}
