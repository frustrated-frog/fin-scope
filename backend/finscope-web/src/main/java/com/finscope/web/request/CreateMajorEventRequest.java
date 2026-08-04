package com.finscope.web.request;

import com.finscope.domain.majorevent.MajorEventCreateCommand;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateMajorEventRequest {
    private String originType;
    private String originKey;
    private LocalDate occurredDate;
    private String note;
    private String title;
    private String summary;
    private String sourceName;
    private String sourceUrl;
    private String categoryCode;

    public MajorEventCreateCommand toCommand() {
        MajorEventCreateCommand command = new MajorEventCreateCommand();
        command.setOriginType(originType);
        command.setOriginKey(originKey);
        command.setOccurredDate(occurredDate);
        command.setNote(note);
        command.setTitle(title);
        command.setSummary(summary);
        command.setSourceName(sourceName);
        command.setSourceUrl(sourceUrl);
        command.setCategoryCode(categoryCode);
        return command;
    }
}
