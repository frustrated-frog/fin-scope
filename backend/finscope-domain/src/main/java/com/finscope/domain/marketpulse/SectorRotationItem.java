package com.finscope.domain.marketpulse;

import com.finscope.common.enums.marketpulse.SectorRotationStage;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SectorRotationItem {
    private String sectorCode;
    private String sectorName;
    private Double return1d;
    private Double return5d;
    private Double return20d;
    private Double excessReturn5d;
    private Double mainNetInflow;
    private Integer flowRank;
    private Integer previousFlowRank;
    private Double breadthRatio;
    private int persistenceDays;
    private int crowdingScore;
    private int rotationScore;
    private SectorRotationStage stage;
    private List<SectorRotationPoint> rotationTrail = new ArrayList<>();
    private List<String> explanations = new ArrayList<>();
}
