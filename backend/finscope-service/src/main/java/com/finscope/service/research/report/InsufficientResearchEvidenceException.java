package com.finscope.service.research.report;

public class InsufficientResearchEvidenceException extends IllegalStateException {
    public InsufficientResearchEvidenceException(String message) {
        super(message);
    }
}
