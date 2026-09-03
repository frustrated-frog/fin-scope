package com.finscope.domain.majorevent;

import lombok.Data;

import java.time.LocalDate;

@Data
public class MajorEventCreateCommand {
    private String originType;
    private String originKey;
    private LocalDate occurredDate;
    private String note;
    public static MajorEventCreateCommand article(Long articleId, LocalDate occurredDate, String note) {
        MajorEventCreateCommand command = new MajorEventCreateCommand();
        command.setOriginType("ARTICLE");
        command.setOriginKey(String.valueOf(articleId));
        command.setOccurredDate(occurredDate);
        command.setNote(note);
        return command;
    }
}
