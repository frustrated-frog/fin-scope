package com.finscope.service.research.report;

import com.finscope.domain.research.ResearchThesis;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ResearchReportSynthesisAgent {
    private final ResearchEvidenceDossierBuilder dossierBuilder;
    private final ResearchReportBlueprintAgent blueprintAgent;
    private final ResearchReportNarrativeAgent narrativeAgent;
    private final StructuredResearchReportAssembler assembler;
    private final ResearchReportQualityValidator qualityValidator;

    public ResearchReportSynthesisAgent(ResearchEvidenceDossierBuilder dossierBuilder,
                                        ResearchReportBlueprintAgent blueprintAgent,
                                        ResearchReportNarrativeAgent narrativeAgent,
                                        StructuredResearchReportAssembler assembler,
                                        ResearchReportQualityValidator qualityValidator) {
        this.dossierBuilder = dossierBuilder;
        this.blueprintAgent = blueprintAgent;
        this.narrativeAgent = narrativeAgent;
        this.assembler = assembler;
        this.qualityValidator = qualityValidator;
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
            List<String> issues = qualityValidator.validate(markdown, thesis, dossier);
            if (!issues.isEmpty()) {
                return fallback(fallback, "REPORT_QUALITY_REJECTED:" + String.join(",", issues));
            }
            String title = value(thesis.getSubjectName(), "研究命题") + "深度研究报告";
            return new GeneratedResearchReport(title, blueprint.getDirectAnswer(), blueprint.getDirection(),
                    blueprint.getConfidence(), narrative.getExecutiveSummary(), markdown,
                    blueprint.isRepaired() || narrative.isRepaired() ? "MODEL_REPAIRED" : "MODEL_STRUCTURED", "");
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
}
