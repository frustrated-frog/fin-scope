package com.finscope.domain.marketpulse;

import lombok.Data;

@Data
public class MarketNewHighLow {
    private Integer high20Count;
    private Integer low20Count;
    private Integer valid20Count;
    private Integer high60Count;
    private Integer low60Count;
    private Integer valid60Count;
    private Integer high250Count;
    private Integer low250Count;
    private Integer valid250Count;
}
