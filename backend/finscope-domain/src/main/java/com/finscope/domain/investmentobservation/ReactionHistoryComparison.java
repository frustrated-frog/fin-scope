package com.finscope.domain.investmentobservation;

import lombok.Data;

@Data
public class ReactionHistoryComparison {
    private int sessions;
    private ReactionComparisonGroup sameCompany;
    private ReactionComparisonGroup otherCompanies;
}
