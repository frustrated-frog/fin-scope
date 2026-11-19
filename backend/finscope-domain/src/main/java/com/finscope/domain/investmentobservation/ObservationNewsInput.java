package com.finscope.domain.investmentobservation;

import com.finscope.domain.news.ai.NewsAiAnalysis;
import java.time.LocalDateTime;

public record ObservationNewsInput(String reportId, int version, String eventKey, String title, String body,
                                   String url, LocalDateTime published, LocalDateTime captured,
                                   NewsAiAnalysis analysis, String checkpoint, String context, boolean policyRepresentative) {
    public boolean containsEvidence(String quote) {
        return quote != null && !quote.isBlank() && (title.contains(quote) || body.contains(quote));
    }
}
