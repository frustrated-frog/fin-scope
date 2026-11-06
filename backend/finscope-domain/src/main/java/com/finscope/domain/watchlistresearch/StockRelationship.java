package com.finscope.domain.watchlistresearch;

import lombok.Data;
import java.time.LocalDate;

@Data
public class StockRelationship {
    private String leftCode;
    private String leftName;
    private String rightCode;
    private String rightName;
    private LocalDate from;
    private LocalDate through;
    private int samples;
    private Double correlation;
    private Double residualCorrelation;
    private Double downsideCorrelation;
    private int downsideSamples;
    private boolean strong;
}
