package com.finscope.service.research.report;

import com.finscope.domain.research.ResearchThesis;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ResearchReportSynthesisAgent {
    private final ResearchEvidenceDossierBuilder dossierBuilder;
    private final ResearchReportBlueprintAgent blueprintAgent;
    private final ResearchReportNarrativeAgent narrativeAgent;
    private final StructuredResearchReportAssembler assembler;
    private final ResearchReportQualityValidator qualityValidator;
    private final ResearchClaimAuditor claimAuditor;
    private final ResearchReportRepairAgent repairAgent;

    public ResearchReportSynthesisAgent(ResearchEvidenceDossierBuilder dossierBuilder,
                                        ResearchReportBlueprintAgent blueprintAgent,
                                        ResearchReportNarrativeAgent narrativeAgent,
                                        StructuredResearchReportAssembler assembler,
                                        ResearchReportQualityValidator qualityValidator) {
        this(dossierBuilder, blueprintAgent, narrativeAgent, assembler, qualityValidator,
                new ResearchClaimAuditor(new ResearchClaimExtractor()), new ResearchReportRepairAgent(null));
    }

    @Autowired
    public ResearchReportSynthesisAgent(ResearchEvidenceDossierBuilder dossierBuilder,
                                        ResearchReportBlueprintAgent blueprintAgent,
                                        ResearchReportNarrativeAgent narrativeAgent,
                                        StructuredResearchReportAssembler assembler,
                                        ResearchReportQualityValidator qualityValidator,
                                        ResearchClaimAuditor claimAuditor,
                                        ResearchReportRepairAgent repairAgent) {
        this.dossierBuilder = dossierBuilder;
        this.blueprintAgent = blueprintAgent;
        this.narrativeAgent = narrativeAgent;
        this.assembler = assembler;
        this.qualityValidator = qualityValidator;
        this.claimAuditor = claimAuditor;
        this.repairAgent = repairAgent;
    }

    public GeneratedResearchReport refine(ResearchThesis thesis, List<ResearchEvidenceCard> evidence,
                                           GeneratedResearchReport fallback) {
        if (!EvidenceSufficiency.assess(evidence).isSufficient()) {
            return fallback(fallback, "EVIDENCE_INSUFFICIENT_FOR_MODEL_REPORT");
        }
        try {
            List<ResearchEvidenceDossier> dossier = dossierBuilder.build(evidence);
            ResearchReportBlueprint blueprint = blueprintAgent.generate(thesis, dossier);
            ResearchReportNarrative narrative = narrativeAgent.generate(thesis, blueprint, dossier);
            String markdown = assembler.assemble(thesis, blueprint, narrative, dossier);
            ResearchClaimAudit audit = claimAuditor.audit(markdown, dossier);
            boolean claimRepaired = false;
            if (audit.hasBlockingIssues()) {
                markdown = repairAgent.repair(markdown, audit, dossier);
                claimRepaired = true;
                audit = claimAuditor.audit(markdown, dossier);
                if (audit.hasBlockingIssues()) {
                    return fallback(fallback, "CLAIM_AUDIT_REJECTED:" + auditSummary(audit));
                }
            }
            List<String> issues = qualityValidator.validate(markdown, thesis, dossier);
            if (!issues.isEmpty()) {
                return fallback(fallback, "REPORT_QUALITY_REJECTED:" + String.join(",", issues));
            }
            String title = value(thesis.getSubjectName(), "研究命题") + "深度研究报告";
            return new GeneratedResearchReport(title, blueprint.getDirectAnswer(), blueprint.getDirection(),
                    blueprint.getConfidence(), narrative.getExecutiveSummary(), markdown,
                    claimRepaired ? "MODEL_CLAIM_REPAIRED"
                            : blueprint.isRepaired() || narrative.isRepaired() ? "MODEL_REPAIRED" : "MODEL_STRUCTURED",
                    claimRepaired ? "CLAIM_AUDIT_REPAIRED:" + auditSummary(audit) : "");
        } catch (ResearchReportGenerationException ex) {
            return fallback(fallback, ex.getMessage());
        } catch (Exception ex) {
            return fallback(fallback, "REPORT_PIPELINE_FAILED:" + ex.getClass().getSimpleName());
        }
    }

    private GeneratedResearchReport fallback(GeneratedResearchReport source, String warning) {
        return new GeneratedResearchReport(source.getTitle(), source.getConclusion(), source.getConclusionDirection(),
                source.getConfidence(), source.getExecutiveSummary(), source.getMarkdown(),
                "EVIDENCE_STRUCTURED_FALLBACK", warning);
    }

    private String value(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private String auditSummary(ResearchClaimAudit audit) {
        return "claims=" + audit.getClaimCount() + ",supported=" + audit.getSupportedCount()
                + ",partial=" + audit.getPartialCount() + ",unsupported=" + audit.getUnsupportedCount()
                + ",conflict=" + audit.getConflictCount();
    }
}
