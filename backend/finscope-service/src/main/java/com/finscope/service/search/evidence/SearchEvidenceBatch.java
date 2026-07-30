package com.finscope.service.search.evidence;

import java.util.Collections;
import java.util.List;

public class SearchEvidenceBatch {
    private final List<SearchEvidence> evidence;
    private final List<SearchProviderDiagnostic> diagnostics;
    private final boolean allProvidersFailed;

    public SearchEvidenceBatch(List<SearchEvidence> evidence,
                               List<SearchProviderDiagnostic> diagnostics,
                               boolean allProvidersFailed) {
        this.evidence = evidence == null ? Collections.<SearchEvidence>emptyList() : evidence;
        this.diagnostics = diagnostics == null
                ? Collections.<SearchProviderDiagnostic>emptyList() : diagnostics;
        this.allProvidersFailed = allProvidersFailed;
    }

    public List<SearchEvidence> getEvidence() { return evidence; }
    public List<SearchProviderDiagnostic> getDiagnostics() { return diagnostics; }
    public boolean isAllProvidersFailed() { return allProvidersFailed; }
}
