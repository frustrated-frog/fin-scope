package com.finscope.domain.marketintel;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
public class CapitalInterpretation {
    private Long id;
    private Long instrumentId;
    private Long snapshotId;
    private String interpretationType;
    private String status;
    private String plainSummary;
    private List<String> facts = Collections.emptyList();
    private List<CapitalHypothesis> hypotheses = Collections.emptyList();
    private List<String> dataGaps = Collections.emptyList();
    private List<String> observationPoints = Collections.emptyList();
    private String disclaimer;
    private String fallbackReason;
    private String ruleVersion;
    private String modelName;
    private String promptVersion;
    private String inputHash;
    private String outputHash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public void setFacts(List<String> values) { facts = immutable(values); }
    public void setHypotheses(List<CapitalHypothesis> values) { hypotheses = immutable(values); }
    public void setDataGaps(List<String> values) { dataGaps = immutable(values); }
    public void setObservationPoints(List<String> values) { observationPoints = immutable(values); }
    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values == null ? Collections.<T>emptyList() : values));
    }
}
