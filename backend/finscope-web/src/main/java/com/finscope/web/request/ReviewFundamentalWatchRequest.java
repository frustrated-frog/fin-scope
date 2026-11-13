package com.finscope.web.request;

import com.finscope.common.enums.fundamentals.FundamentalWatchStatus;
import lombok.Data;
import javax.validation.constraints.*;
import com.finscope.domain.fundamentals.FundamentalWatchReviewCommand;
import java.time.LocalDate;

@Data
public class ReviewFundamentalWatchRequest {
    @NotNull
    private FundamentalWatchStatus status;
    @NotBlank @Size(max = 2000)
    private String note;
    @NotNull @Min(0)
    private Integer revision;
    private LocalDate nextReviewDate;
    @Pattern(regexp = "[a-f0-9]{64}")
    private String comparisonToken;

    public FundamentalWatchReviewCommand toCommand() {
        var command = new FundamentalWatchReviewCommand();
        command.setRevision(revision);
        command.setStatus(status);
        command.setNote(note);
        command.setNextReviewDate(nextReviewDate);
        command.setComparisonToken(comparisonToken);
        return command;
    }
}
