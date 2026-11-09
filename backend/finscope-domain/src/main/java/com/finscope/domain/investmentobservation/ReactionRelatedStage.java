package com.finscope.domain.investmentobservation;

import com.finscope.common.enums.investmentobservation.ReactionEventSubtype;

import java.time.LocalDateTime;

public record ReactionRelatedStage(long sampleId, String title, ReactionEventSubtype subtype, LocalDateTime publishedAt) { }
