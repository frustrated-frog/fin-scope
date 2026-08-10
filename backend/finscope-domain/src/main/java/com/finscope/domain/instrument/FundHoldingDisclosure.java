package com.finscope.domain.instrument;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 基金最近一期公开披露的直接股票持仓。 */
public final class FundHoldingDisclosure {
    private final String fundCode;
    private final String fundName;
    private final LocalDate disclosureDate;
    private final LocalDateTime retrievedAt;
    private final List<FundStockHolding> holdings;

    public FundHoldingDisclosure(String fundCode, String fundName, LocalDate disclosureDate,
                                 LocalDateTime retrievedAt, List<FundStockHolding> holdings) {
        this.fundCode = fundCode;
        this.fundName = fundName;
        this.disclosureDate = disclosureDate;
        this.retrievedAt = retrievedAt;
        this.holdings = Collections.unmodifiableList(new ArrayList<FundStockHolding>(holdings));
    }

    public String getFundCode() { return fundCode; }
    public String getFundName() { return fundName; }
    public LocalDate getDisclosureDate() { return disclosureDate; }
    public LocalDateTime getRetrievedAt() { return retrievedAt; }
    public List<FundStockHolding> getHoldings() { return holdings; }
}
