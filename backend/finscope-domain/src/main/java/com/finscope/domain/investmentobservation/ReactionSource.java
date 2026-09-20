package com.finscope.domain.investmentobservation;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ReactionSource {
    private String originType;
    private String originKey;
    private String eventKey;
    private String title;
    private String url;
    private LocalDateTime publishedAt;
    private LocalDateTime capturedAt;
}
