package com.finscope.service.financials;

import com.finscope.domain.financials.BrokerResearchCandidate;

import java.time.LocalDate;
import java.util.List;

public interface BrokerResearchSource {
    String sourceCode();
    List<BrokerResearchCandidate> list(String stockCode, LocalDate from, LocalDate to, int limit);
    byte[] download(BrokerResearchCandidate candidate);
}
