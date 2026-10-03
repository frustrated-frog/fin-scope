package com.finscope.domain.attribution;

import lombok.Data;

/** 归因增量研究的独立快照，不覆盖原报告内容。 */
@Data
public class AttributionPeerCandidate {
    private String code;
    private String name;
    private String reason;
}
