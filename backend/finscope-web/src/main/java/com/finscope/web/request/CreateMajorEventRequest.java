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

    public MajorEventCreateCommand toCommand() {
        MajorEventCreateCommand command = new MajorEventCreateCommand();
        command.setOriginType(originType);
        command.setOriginKey(originKey);
        command.setOccurredDate(occurredDate);
        command.setNote(note);
        return command;
    }
}
