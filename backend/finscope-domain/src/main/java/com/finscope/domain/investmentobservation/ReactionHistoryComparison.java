package com.finscope.domain.investmentobservation;

import lombok.Data;
import java.util.List;

@Data
public class ReactionHistoryComparison {
    private int sessions;
    private ReactionComparisonGroup sameCompany;
    private ReactionComparisonGroup otherCompanies;
    private List<ReactionRelatedStage> relatedStages = List.of();
}
