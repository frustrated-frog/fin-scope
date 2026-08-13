package com.finscope.domain.industrychain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 产业链中的环节、产品或公司节点。 */
@Data
public class IndustryChainNode {
    private String nodeKey;
    private String type;
    private String name;
    private String description;
    private Integer stageOrder;
    private String stockCode;
    private String confidence;
    private List<String> evidenceRefs = new ArrayList<>();
}
