package com.finscope.web.response;

import com.finscope.common.enums.investmentobservation.ReactionEventType;
import com.finscope.common.enums.investmentobservation.ReactionSampleState;
import com.finscope.domain.investmentobservation.ReactionSample;
import com.finscope.domain.investmentobservation.ReactionCalculation;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ReactionSampleResponse {
    private Long id;
    private Long majorEventId;
    private String sourceIdentity;
    private boolean automatic;
    private String discoveryIssue;
    private String sourceOriginType;
    private String sourceOriginKey;
    private String title;
    private String summary;
    private String sourceUrl;
    private LocalDate occurredDate;
    private LocalDateTime firstCapturedAt;
    private LocalDateTime registeredAt;
    private String instrumentCode;
    private String instrumentName;
    private ReactionEventType eventType;
    private LocalDateTime publishedAt;
    private String relationNote;
    private ReactionSampleState state;
    private boolean historicalBackfill;
    private int revision;
    private ReactionCalculation calculation;
    private LocalDateTime lastAttemptAt;
    private String refreshError;

    public static ReactionSampleResponse from(ReactionSample sample) {
        ReactionSampleResponse result = new ReactionSampleResponse();
        result.setId(sample.getId());
        result.setMajorEventId(sample.getMajorEventId());
        result.setSourceIdentity(sample.getSourceIdentity());
        result.setAutomatic(sample.isAutomatic());
        result.setDiscoveryIssue(sample.getDiscoveryIssue());
        result.setSourceOriginType(sample.getSourceOriginType());
        result.setSourceOriginKey(sample.getSourceOriginKey());
        result.setTitle(sample.getTitle());
        result.setSummary(sample.getSummary());
        result.setSourceUrl(sample.getSourceUrl());
        result.setOccurredDate(sample.getOccurredDate());
        result.setFirstCapturedAt(sample.getFirstCapturedAt());
        result.setRegisteredAt(sample.getRegisteredAt());
        result.setInstrumentCode(sample.getInstrumentCode());
        result.setInstrumentName(sample.getInstrumentName());
        result.setEventType(sample.getEventType());
        result.setPublishedAt(sample.getPublishedAt());
        result.setRelationNote(sample.getRelationNote());
        result.setState(sample.getState());
        result.setHistoricalBackfill(sample.isHistoricalBackfill());
        result.setRevision(sample.getRevision());
        result.setCalculation(sample.getCalculation());
        result.setLastAttemptAt(sample.getLastAttemptAt());
        result.setRefreshError(sample.getRefreshError());
        return result;
    }
}
