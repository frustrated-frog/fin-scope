package com.finscope.domain.quant.paper;

import com.finscope.common.enums.quant.PaperUniverse;

import java.math.BigDecimal;

/** Research intent and the provenance of a frozen intraday experiment. */
public record PaperResearchInput(String hypothesis, PaperUniverse pool, BigDecimal capital,
                                 Long academyVersionId, String factorCode, Long parent) {
}
