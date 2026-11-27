package com.finscope.domain.quant.statistics;

import java.time.LocalDate;

public record FinancialStatisticsInput(String asset, String peer, LocalDate asOf, int days, int window,
                                      double annualRiskFree) {
}
