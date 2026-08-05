package com.finscope.domain.research.material;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Redis 中缓存的一次研究资料查询结果；Redis 失效时不影响 SQLite 主链路。 */
public class ResearchMaterialCacheEntry {
    private List<ResearchMaterial> materials = Collections.emptyList();
    private List<String> warnings = Collections.emptyList();
    private LocalDateTime fetchedAt;

    public ResearchMaterialCacheEntry() {
    }

    public ResearchMaterialCacheEntry(List<ResearchMaterial> materials,
                                      List<String> warnings,
                                      LocalDateTime fetchedAt) {
        setMaterials(materials);
        setWarnings(warnings);
        this.fetchedAt = fetchedAt;
    }

    public List<ResearchMaterial> getMaterials() {
        return materials;
    }

    public void setMaterials(List<ResearchMaterial> materials) {
        this.materials = materials == null
                ? Collections.<ResearchMaterial>emptyList()
                : Collections.unmodifiableList(new ArrayList<ResearchMaterial>(materials));
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(warnings));
    }

    public LocalDateTime getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(LocalDateTime fetchedAt) {
        this.fetchedAt = fetchedAt;
    }
}
