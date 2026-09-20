package com.finscope.service.research.material;

import com.finscope.domain.news.NewsSourceHealth;

import com.finscope.domain.research.material.ResearchMaterial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ResearchMaterialGatewayResult {
    @lombok.Getter
    @lombok.Setter
    private List<NewsSourceHealth> sourceHealth = Collections.emptyList();
    private final List<ResearchMaterial> materials;
    private final List<String> warnings;

    public ResearchMaterialGatewayResult(List<ResearchMaterial> materials, List<String> warnings) {
        this.materials = Collections.unmodifiableList(new ArrayList<ResearchMaterial>(materials));
        this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
    }

    public List<ResearchMaterial> getMaterials() { return materials; }
    public List<String> getWarnings() { return warnings; }
}
