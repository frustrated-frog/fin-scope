package com.finscope.domain.marketpulse;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 单个同花顺行业在指定业务日之前的历史收益截面。 */
@Data
public class SectorHistoryItem {
    private String sectorCode;
    private String sectorName;
    private LocalDate lastTradeDate;
    private int coverageDays;
    private Double return1d;
    private Double return5d;
    private Double return20d;
    private Integer positiveDays5;
    private List<SectorRotationPoint> rotationTrail = new ArrayList<>();
}
