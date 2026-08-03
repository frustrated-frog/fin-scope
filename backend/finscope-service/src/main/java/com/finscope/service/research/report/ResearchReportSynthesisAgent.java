package com.finscope.service.research.report;

import com.finscope.domain.research.ResearchThesis;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.ArrayList;

@Component
public class ResearchReportSynthesisAgent {
    private final ResearchEvidenceDossierBuilder dossierBuilder;
    private final ResearchReportBlueprintAgent blueprintAgent;
    private final ResearchReportNarrativeAgent narrativeAgent;
    private final StructuredResearchReportAssembler assembler;
    private final ResearchReportQualityValidator qualityValidator;
    private final ResearchClaimAuditor claimAuditor;
    private final ResearchReportRepairAgent repairAgent;
    private final DeterministicReportBlueprintBuilder baselineBlueprintBuilder;
    private final DeterministicReportNarrativeBuilder baselineNarrativeBuilder;

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
        this.baselineBlueprintBuilder = new DeterministicReportBlueprintBuilder();
        this.baselineNarrativeBuilder = new DeterministicReportNarrativeBuilder();
    }

    public GeneratedResearchReport refine(ResearchThesis thesis, List<ResearchEvidenceCard> evidence,
                                           GeneratedResearchReport fallback) {
        if (!EvidenceSufficiency.assess(evidence).isSufficient()) {
            return fallback(fallback, "EVIDENCE_INSUFFICIENT_FOR_MODEL_REPORT");
        }
        List<String> diagnostics = new ArrayList<String>();
        try {
            List<ResearchEvidenceDossier> dossier = dossierBuilder.build(evidence);
            ResearchReportBlueprint blueprint;
            try {
                blueprint = blueprintAgent.generate(thesis, dossier);
                diagnostics.addAll(blueprint.getDiagnostics());
            } catch (Exception error) {
                blueprint = baselineBlueprintBuilder.build(thesis, dossier, true);
                diagnostics.add(diagnostic("BLUEPRINT_MODEL_STAGE_FAILED", error));
            }
            ResearchReportNarrative narrative;
            try {
                narrative = narrativeAgent.generate(thesis, blueprint, dossier);
                diagnostics.addAll(narrative.getDiagnostics());
            } catch (Exception error) {
                narrative = baselineNarrativeBuilder.build(thesis, blueprint, dossier);
                diagnostics.add(diagnostic("NARRATIVE_MODEL_STAGE_FAILED", error));
            }
            String markdown = assembler.assemble(thesis, blueprint, narrative, dossier);
            ResearchClaimAudit audit = claimAuditor.audit(markdown, dossier);
            boolean claimRepaired = false;
            if (audit.hasBlockingIssues()) {
                markdown = repairAgent.repair(markdown, audit, dossier);
                claimRepaired = true;
                audit = claimAuditor.audit(markdown, dossier);
                if (audit.hasBlockingIssues()) {
                    diagnostics.add("CLAIM_AUDIT_REJECTED:" + auditSummary(audit));
                    return fallback(fallback, String.join(";", diagnostics));
                }
            }
            List<String> issues = qualityValidator.validate(markdown, thesis, dossier);
            if (!issues.isEmpty()) {
                diagnostics.add("REPORT_QUALITY_REJECTED:" + String.join(",", issues));
                return fallback(fallback, String.join(";", diagnostics));
            }
            if (!narrative.isModelEnhanced()) {
                if (diagnostics.isEmpty()) diagnostics.add("NARRATIVE_MODEL_COVERAGE_INSUFFICIENT");
                return fallback(fallback, String.join(";", diagnostics));
            }
            String title = value(thesis.getSubjectName(), "研究命题") + "深度研究报告";
            boolean modelRepaired = blueprint.isRepaired() || narrative.isRepaired()
                    || !blueprint.isModelEnhanced() || !diagnostics.isEmpty();
            String mode = claimRepaired ? "MODEL_CLAIM_REPAIRED"
                    : modelRepaired ? "MODEL_REPAIRED" : "MODEL_STRUCTURED";
            if (claimRepaired) diagnostics.add("CLAIM_AUDIT_REPAIRED:" + auditSummary(audit));
            return new GeneratedResearchReport(title, blueprint.getDirectAnswer(), blueprint.getDirection(),
                    blueprint.getConfidence(), narrative.getExecutiveSummary(), markdown,
                    mode, String.join(";", diagnostics));
        } catch (Exception ex) {
            diagnostics.add("REPORT_PIPELINE_FAILED:" + ex.getClass().getSimpleName());
            return fallback(fallback, String.join(";", diagnostics));
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

    private String diagnostic(String stage, Exception error) {
        if (error instanceof ResearchReportGenerationException) {
            String message = error.getMessage();
            if (message != null && !message.trim().isEmpty()) {
                String clean = message.replaceAll("[\\r\\n]+", " ").trim();
                return clean.length() <= 300 ? clean : clean.substring(0, 300);
            }
        }
        return stage + ":" + error.getClass().getSimpleName();
    }
}
