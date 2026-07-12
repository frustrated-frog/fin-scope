package com.finscope.service.strategy;

public class StrategyPlaybookView {
    public final String code,title,scope,summary,cadence,riskBoundary,status,note; public final long revision;
    public StrategyPlaybookView(String code,String title,String scope,String summary,String cadence,String riskBoundary,String status,String note,long revision){this.code=code;this.title=title;this.scope=scope;this.summary=summary;this.cadence=cadence;this.riskBoundary=riskBoundary;this.status=status;this.note=note;this.revision=revision;}
}
