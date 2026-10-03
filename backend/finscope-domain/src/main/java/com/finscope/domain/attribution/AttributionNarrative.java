package com.finscope.domain.attribution;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 面向普通用户的每日涨跌因果叙事。 */
@Data
public class AttributionNarrative {
    /** 多条消息之间的强化、抵消或条件关系。 */
    private String interactionAnalysis;
    /** 消息方向与实际价格表现之间的关系及可能机制。 */
    private String priceNewsDivergence;
    private String plainSummary;
    private String event;
    private String instrumentLink;
    private String whyToday;
    private List<String> causalSteps = new ArrayList<String>();
    private List<String> amplifiers = new ArrayList<String>();
    private List<String> dampeners = new ArrayList<String>();
}
