package com.finscope.domain.investmentrecognition;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class InvestmentRecognitionRun {
    private int checkedObjects;
    private int candidateCount;
    private int needsEvidenceCount;
    private LocalDateTime generatedAt;
    private List<InvestmentRecognitionCandidate> candidates = new ArrayList<InvestmentRecognitionCandidate>();
}
