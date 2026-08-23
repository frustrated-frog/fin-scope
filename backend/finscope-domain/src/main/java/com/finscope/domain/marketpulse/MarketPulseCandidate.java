package com.finscope.domain.marketpulse;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MarketPulseCandidate {
    private String instrumentCode;
    private String name;
    private Integer researchRank;
    private Double calibratedProbability;
    private String healthStatus;
    private String sectorName;
    private String sectorStage;
    private String whyNow;
    private List<String> reasons = new ArrayList<>();
    private List<String> risks = new ArrayList<>();
    private List<String> invalidationConditions = new ArrayList<>();
}
