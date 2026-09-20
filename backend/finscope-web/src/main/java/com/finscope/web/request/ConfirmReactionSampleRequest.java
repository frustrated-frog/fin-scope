package com.finscope.web.request;

import com.finscope.common.enums.investmentobservation.ReactionEventType;
import com.finscope.domain.investmentobservation.ReactionRegistration;
import lombok.Data;
import javax.validation.constraints.*;
import java.time.LocalDateTime;

@Data
public class ConfirmReactionSampleRequest {
    @NotBlank
    @Pattern(regexp = "(?:6[0-9]{5}\\.SH|[03][0-9]{5}\\.SZ|[489][0-9]{5}\\.BJ)")
    private String instrumentCode;
    @NotBlank
    @Size(max = 80)
    private String instrumentName;
    @NotNull
    private ReactionEventType eventType;
    @NotNull
    private LocalDateTime publishedAt;
    @NotBlank
    @Size(max = 1000)
    private String relationNote;
    @NotNull
    @Min(0)
    private Integer revision;

    public ReactionRegistration toCommand() {
        ReactionRegistration command = new ReactionRegistration();
        command.setInstrumentCode(instrumentCode);
        command.setInstrumentName(instrumentName);
        command.setEventType(eventType);
        command.setPublishedAt(publishedAt);
        command.setRelationNote(relationNote);
        command.setRevision(revision);
        return command;
    }
}
