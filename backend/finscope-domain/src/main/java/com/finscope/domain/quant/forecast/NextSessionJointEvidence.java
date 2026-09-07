package com.finscope.domain.quant.forecast;

import lombok.Data;
import com.finscope.common.enums.quant.JointEvidenceKind;
import com.finscope.common.enums.quant.JointReturnTarget;

/** Independent temporal evidence for joint next-close prediction and cross-sectional ranking. */
@Data
public class NextSessionJointEvidence {
    private String modelVersion;
    private Integer trainingUniverseCount;
    private Integer displayUniverseCount;
    private Double industryCoverage;
    private JointReturnTarget returnTarget;
    private JointReturnTarget rankingTarget;
    private Double selectionReturnMse;
    private JointEvidenceKind evidenceKind;
    private String selectedClassifier;
    private boolean applied;
    private boolean classificationEligible;
    private boolean rankingEligible;
    private int featureCount;
    private int universeCount;
    private int trainingSampleCount;
    private int validationSampleCount;
    private int validationDayCount;
    private String testStart;
    private String testEnd;
    private Double selectionBrierScore;
    private Double selectionRankIc;
    private Double pooledBrierScore;
    private Double baselineBrierScore;
    private Double logisticBrierScore;
    private Double accuracy;
    private Double intervalCoverage;
    private Double regressionMse;
    private Double baselineRegressionMse;
    private Double rankIc;
    private Double top5Return;
    private Double top5PoolExcess;
    private Double top5MomentumExcess;
    private Double rankingScore;
    private Double rankingPercentile;
    private int stockValidationCount;
    private Double stockBrierScore;
    private Double stockBaselineBrierScore;
    private Double upProbability;
    private Double expectedReturn;
    private String reason;
}
