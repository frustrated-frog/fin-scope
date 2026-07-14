package com.finscope.domain.agent;

public final class AgentTraceSubject {
    /**
     * 类型。
     */
    private final String type;
    /**
     * 主键 ID。
     */
    private final Long id;
    private AgentTraceSubject(String type,Long id){if(type==null||type.trim().isEmpty())throw new IllegalArgumentException("subject type is required");this.type=type;this.id=id;}
    public static AgentTraceSubject of(String type,Long id){return new AgentTraceSubject(type,id);}
    public String getType(){return type;}public Long getId(){return id;}
}
