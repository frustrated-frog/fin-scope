package com.finscope.rpc.marketpulse;

import com.finscope.domain.marketpulse.MarketBreadthSnapshot;

import java.time.LocalDate;

public interface MarketBreadthSource {
    MarketBreadthSnapshot fetch(LocalDate businessDate);
}
