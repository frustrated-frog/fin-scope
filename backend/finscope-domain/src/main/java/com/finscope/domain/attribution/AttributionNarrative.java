package com.finscope.domain.attribution;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 面向普通用户的每日涨跌因果叙事。 */
@Data
public class AttributionNarrative {
    private String plainSummary;
    private String event;
    private String instrumentLink;
    private String whyToday;
    private List<String> causalSteps = new ArrayList<String>();
    private List<String> amplifiers = new ArrayList<String>();
    private List<String> dampeners = new ArrayList<String>();
}
