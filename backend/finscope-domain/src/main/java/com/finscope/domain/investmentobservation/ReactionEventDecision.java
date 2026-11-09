package com.finscope.domain.investmentobservation;

import lombok.Data;
import com.finscope.common.enums.investmentobservation.ReactionEventSubtype;
import com.finscope.common.enums.investmentobservation.ReactionEventType;
import java.util.ArrayList;
import java.util.List;

@Data
public class ReactionEventDecision {
    static final String CURRENT_RULE_VERSION = "REACTION_RULES_V3";
    private ReactionEventType eventType;
    private ReactionEventSubtype subtype = ReactionEventSubtype.UNCLASSIFIED;
    private String ruleVersion = CURRENT_RULE_VERSION;
    private String evidence;
    private String fact;
    private String mergeAnchor;
    private List<String> subjects = new ArrayList<>();
}
