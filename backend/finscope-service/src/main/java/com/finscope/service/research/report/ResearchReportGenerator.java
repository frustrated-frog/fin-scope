package com.finscope.service.research.report;

import com.finscope.domain.research.ResearchThesis;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ResearchReportGenerator {
    private final ResearchEvidenceDossierBuilder dossierBuilder;
    private final DeterministicReportBlueprintBuilder blueprintBuilder;
    private final DeterministicReportNarrativeBuilder narrativeBuilder;
    private final StructuredResearchReportAssembler assembler;

    public ResearchReportGenerator() {
        this(new ResearchEvidenceDossierBuilder(), new DeterministicReportBlueprintBuilder(),
                new DeterministicReportNarrativeBuilder(), new StructuredResearchReportAssembler());
    }

    ResearchReportGenerator(ResearchEvidenceDossierBuilder dossierBuilder,
                            DeterministicReportBlueprintBuilder blueprintBuilder,
                            DeterministicReportNarrativeBuilder narrativeBuilder,
                            StructuredResearchReportAssembler assembler) {
        this.dossierBuilder = dossierBuilder;
        this.blueprintBuilder = blueprintBuilder;
        this.narrativeBuilder = narrativeBuilder;
        this.assembler = assembler;
    }

    public GeneratedResearchReport generate(ResearchThesis thesis, List<ResearchEvidenceCard> evidence,
                                             EvidenceSufficiency sufficiency) {
        List<ResearchEvidenceDossier> dossier = dossierBuilder.build(evidence);
        ResearchReportBlueprint blueprint = blueprintBuilder.build(thesis, dossier, sufficiency.isSufficient());
        ResearchReportNarrative narrative = narrativeBuilder.build(thesis, blueprint, dossier);
        String title = value(thesis.getSubjectName(), "研究命题") + "深度研究报告";
        String markdown = assembler.assemble(thesis, blueprint, narrative, dossier);
        return new GeneratedResearchReport(title, blueprint.getDirectAnswer(), blueprint.getDirection(),
                blueprint.getConfidence(),
                ResearchReportPolicy.bound(narrative.getExecutiveSummary(),
                        ResearchReportPolicy.MAX_EXECUTIVE_SUMMARY_CHARACTERS),
                ResearchReportPolicy.bound(markdown, ResearchReportPolicy.MAX_REPORT_CHARACTERS), "DETERMINISTIC");
    }

    private String value(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
