package com.finscope.service.research.evidence;

public final class ResearchEvidenceChunk {
    private final int index;
    private final String text;

    public ResearchEvidenceChunk(int index, String text) {
        this.index = index;
        this.text = text;
    }

    public int getIndex() { return index; }
    public String getText() { return text; }
}
