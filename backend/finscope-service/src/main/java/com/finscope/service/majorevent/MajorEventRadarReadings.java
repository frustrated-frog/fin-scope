package com.finscope.service.majorevent;

import com.finscope.dao.majorevent.MajorEventReadingRepository;
import com.finscope.dao.radar.RadarEventInterpretationRepository;
import com.finscope.domain.news.NewsReportInterpretation;
import com.finscope.domain.radar.RadarEventInterpretation;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class MajorEventRadarReadings {
    @Resource
    private MajorEventReadingRepository archive;
    @Resource
    private RadarEventInterpretationRepository interpretations;

    public boolean capture(String eventKey, long eventId) {
        var saved = interpretations.latestSuccess(eventId);
        saved.ifPresent(value -> retain(eventKey, eventId, value.getResult()));
        return saved.isPresent();
    }

    public void retain(String eventKey, long eventId, RadarEventInterpretation.Result source) {
        var result = new NewsReportInterpretation.Result();
        result.setReading(source.getReading());
        result.setFacts(source.getFactSummary() == null ? List.of() : List.of(source.getFactSummary()));
        result.setUnknowns(source.getUncertainties());
        result.setNextChecks(source.getNextObservations());
        if (source.getReading() != null) {
            result.setFormatVersion(NewsReportInterpretation.CURRENT_FORMAT);
            result.setWhyItMatters(source.getWhyItMatters() == null ? "" : source.getWhyItMatters());
        } else {
            // 早期雷达结果沿用段落式展示，保留新进展、重要性与影响链。
            result.setFormatVersion(2);
            result.setWhyItMatters(Stream.concat(Stream.of(source.getNewDevelopment(), source.getWhyItMatters()),
                            source.getImpactChain().stream()).filter(value -> value != null && !value.isBlank())
                    .collect(Collectors.joining("\n\n")));
        }
        archive.retainRadar(eventKey, eventId, result);
    }
}
