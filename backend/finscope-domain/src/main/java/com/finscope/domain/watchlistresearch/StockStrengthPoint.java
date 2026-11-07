package com.finscope.domain.watchlistresearch;

import java.time.LocalDate;

/** Cumulative returns measured from the start of a strength evaluation. */
public record StockStrengthPoint(LocalDate date, double actualReturnPct, double baselineReturnPct) {
}
