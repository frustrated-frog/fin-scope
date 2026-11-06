package com.finscope.domain.research.quality;

import lombok.Data;

/** Immutable source snapshot text; quote offsets refer to this text, never a cleaned display copy. */
@Data
public class ResearchEvidenceDocument {
    private String reference;
    private String title;
    private String url;
    private String source;
    private String text;
    private String publishedDate;
    private boolean snippetOnly;
    private boolean selected;
}
