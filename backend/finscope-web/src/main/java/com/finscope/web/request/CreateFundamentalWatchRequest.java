package com.finscope.web.request;

import com.finscope.domain.fundamentals.FundamentalWatch;
import lombok.Data;
import javax.validation.constraints.*;
import java.time.LocalDate;

@Data
public class CreateFundamentalWatchRequest {
    @NotBlank @Size(max = 160)
    private String originKey;
    @NotBlank @Size(max = 300)
    private String title;
    @NotBlank @Size(max = 2000)
    private String hypothesis;
    @NotBlank @Size(max = 3000)
    private String baseline;
    @NotBlank @Size(max = 2000)
    private String supportCondition;
    @NotBlank @Size(max = 2000)
    private String challengeCondition;
    @NotNull
    private LocalDate nextReviewDate;

    public FundamentalWatch toWatch() {
        var watch = new FundamentalWatch();
        watch.setOriginKey(originKey.strip());
        watch.setTitle(title.strip());
        watch.setHypothesis(hypothesis.strip());
        watch.setBaseline(baseline.strip());
        watch.setSupportCondition(supportCondition.strip());
        watch.setChallengeCondition(challengeCondition.strip());
        watch.setNextReviewDate(nextReviewDate);
        return watch;
    }
}
