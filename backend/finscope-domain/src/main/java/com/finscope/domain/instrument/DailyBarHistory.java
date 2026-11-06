package com.finscope.domain.instrument;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 一个来源快照内的行情序列及其观测口径，不拼接不同复权版本。 */
@Data
public class DailyBarHistory {
    private String code;
    private String market;
    private String sourceCode;
    private String qualityStatus;
    private String asOf;
    private String retrievedAt;
    private List<DailyBarPoint> bars = new ArrayList<DailyBarPoint>();
    private List<String> warnings = new ArrayList<String>();
}
