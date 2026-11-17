package com.finscope.domain.radar;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 同一生产批次的公共资讯评价；事实摘要只保存在当前事件，不写入每次排名历史。 */
@Data
public class NewsworthinessAssessment {
    private String version;
    private int importance;
    private int novelty;
    private int heat;
    private int freshness;
    private int quality;
    private int publicScore;
    private int newFactCount;
    private String diversityKey;
    private LocalDateTime evaluatedAt;
    private LocalDateTime meaningfulChangeAt;
    private List<String> knownFacts = new ArrayList<>();
    private String explanation;
}
