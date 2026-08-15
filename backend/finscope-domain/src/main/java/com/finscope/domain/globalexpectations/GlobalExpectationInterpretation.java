package com.finscope.domain.globalexpectations;

import lombok.Data;

@Data
public class GlobalExpectationInterpretation {
    private String status;
    private String happened;
    private String meaning;
    private String relatedVariables;
    private String nextObservation;
    private String failureMessage;
    private String fingerprint;
}
