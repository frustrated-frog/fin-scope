package com.finscope.domain.investmentobservation;

import lombok.Data;
import com.finscope.common.enums.investmentobservation.ReactionEventSubtype;
import com.finscope.common.enums.investmentobservation.ReactionEventType;
import java.util.ArrayList;
import java.util.List;

@Data
public class ReactionEventDecision {
    private ReactionEventType eventType;
    private ReactionEventSubtype subtype = ReactionEventSubtype.UNCLASSIFIED;
    private String ruleVersion = "REACTION_RULES_V1";
    private String evidence;
    private String fact;
    private String mergeAnchor;
    private List<String> subjects = new ArrayList<>();
}
