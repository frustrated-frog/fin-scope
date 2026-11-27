package com.finscope.domain.quant.paper;

import java.time.LocalDate;
import java.util.UUID;

public record LearningEntryInput(UUID token, String period, LocalDate day, String hypothesis,
                                 String criterion, String source) {
}
