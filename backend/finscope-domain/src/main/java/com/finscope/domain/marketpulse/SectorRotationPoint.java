package com.finscope.domain.marketpulse;

import lombok.Data;

import java.time.LocalDate;

/** 行业相对强度及其五日变化在单个交易日的坐标。 */
@Data
public class SectorRotationPoint {
    private LocalDate businessDate;
    private Double relativeStrength;
    private Double relativeMomentum;
}
