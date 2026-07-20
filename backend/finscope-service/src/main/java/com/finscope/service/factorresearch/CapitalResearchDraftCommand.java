package com.finscope.service.factorresearch;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
public final class CapitalResearchDraftCommand {
    private final String instrumentCode;
    private final String instrumentName;
    private final LocalDateTime observedAt;
    private final String signalCode;
    private final Long snapshotId;
    private final String snapshotFingerprint;
    private final List<String> evidenceRefs;
    private final List<String> objectiveTags;

    private static List<String> copy(List<String> values) {
        return values == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(values));
    }
}
