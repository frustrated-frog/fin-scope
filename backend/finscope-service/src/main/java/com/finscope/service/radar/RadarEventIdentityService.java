package com.finscope.service.radar;

import com.finscope.domain.radar.RadarSignal;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** 由稳定事实槽位生成事件身份，避免标题和分类改写产生重复事件。 */
@Service
public class RadarEventIdentityService {
    private final RadarTextAnalyzer analyzer;

    public RadarEventIdentityService(RadarTextAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    public String eventKey(List<RadarSignal> signals) {
        List<RadarSignal> values = signals == null ? Collections.<RadarSignal>emptyList() : signals;
        Set<String> subjects = new TreeSet<String>();
        Set<String> entities = new TreeSet<String>();
        Set<String> actions = new TreeSet<String>();
        Set<String> variables = new TreeSet<String>();
        Set<String> numericFacts = new TreeSet<String>();
        String fallback = "";
        LocalDate firstDate = null;
        for (RadarSignal signal : values) {
            RadarSignalFeatures features = analyzer.extract(signal);
            subjects.addAll(features.getSubjects());
            entities.addAll(features.getEntities());
            for (String action : features.getActions()) {
                actions.add(canonicalAction(action));
            }
            variables.addAll(features.getVariables());
            numericFacts.addAll(features.getNumericFacts());
            if (fallback.isEmpty()) {
                fallback = features.getNormalizedTitle();
            }
            LocalDateTime eventTime = features.getEventTime();
            if (eventTime != null && (firstDate == null || eventTime.toLocalDate().isBefore(firstDate))) {
                firstDate = eventTime.toLocalDate();
            }
        }
        String subject = first(subjects, first(entities, abbreviated(fallback)));
        String action = first(actions, "事件");
        String variable = first(variables, "信息");
        String date = firstDate == null ? "unknown" : firstDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        String numeric = numericFacts.isEmpty() ? "" : ":" + numericFacts.iterator().next();
        return subject + ":" + action + ":" + variable + ":" + date + numeric;
    }

    private String canonicalAction(String action) {
        if ("正式发布".equals(action)) {
            return "发布";
        }
        return action;
    }

    private String first(Set<String> values, String fallback) {
        return values.isEmpty() ? fallback : values.iterator().next();
    }

    private String abbreviated(String value) {
        if (value == null || value.isEmpty()) {
            return "信息";
        }
        return value.length() <= 32 ? value : value.substring(0, 32);
    }
}
