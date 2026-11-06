package com.finscope.domain.research.quality;

import com.finscope.common.enums.research.ResearchFactKind;
import com.finscope.common.enums.research.ResearchEvidenceIssue;
import com.finscope.common.enums.research.ResearchValueRelation;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class StructuredResearchFact {
    private String evidenceRef;
    private String subject;
    private ResearchFactKind kind;
    private String value;
    private String unit;
    private ResearchValueRelation valueRelation = ResearchValueRelation.EXACT;
    private String eventDate;
    private String scope;
    private String quote;
    private int quoteStart = -1;
    private int quoteEnd = -1;
    private boolean verified;
    private List<ResearchEvidenceIssue> issues = new ArrayList<>();
}
