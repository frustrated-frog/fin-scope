package com.finscope.web.request;

import com.finscope.common.enums.investmentobservation.InvestmentObservationDisposition;

public class UpdateInvestmentObservationStateRequest {
    private InvestmentObservationDisposition disposition;
    private Integer revision;

    public InvestmentObservationDisposition getDisposition() { return disposition; }
    public void setDisposition(InvestmentObservationDisposition disposition) { this.disposition = disposition; }
    public Integer getRevision() { return revision; }
    public void setRevision(Integer revision) { this.revision = revision; }
}
