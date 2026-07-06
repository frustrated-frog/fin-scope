package com.finscope.service.fetch;

public class RawItemSignal {
    private final int score;
    private final boolean selectable;
    private final String reason;

    public RawItemSignal(int score, boolean selectable, String reason) {
        this.score = score;
        this.selectable = selectable;
        this.reason = reason;
    }

    public int getScore() {
        return score;
    }

    public boolean isSelectable() {
        return selectable;
    }

    public String getReason() {
        return reason;
    }
}
