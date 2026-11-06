package com.finscope.domain.watchlistresearch;

import lombok.Data;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class StockStrengthAnalysis {
    private String code;
    private String name;
    private String sourceCode;
    private LocalDate trainingFrom;
    private LocalDate trainingThrough;
    private LocalDate evaluatedFrom;
    private LocalDate evaluatedThrough;
    private int trainingSamples;
    private int evaluationSamples;
    private Double beta;
    private Double rSquared;
    private Double actualReturnPct;
    private Double baselineReturnPct;
    private Double excessReturnPp;
    private int positiveResidualDays;
    private List<String> gaps = new ArrayList<String>();
}
