package com.finscope.rpc.marketpulse;

import com.finscope.domain.marketpulse.SectorHistorySnapshot;

import java.time.LocalDate;

/** 指定业务日的全行业历史收益来源。 */
public interface SectorHistorySource {
    SectorHistorySnapshot fetch(LocalDate businessDate, int window);
}
