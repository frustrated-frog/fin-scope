package com.finscope.service.research.agent.tool;

import com.finscope.domain.research.mission.ResearchToolDescriptor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ResearchAgentToolRegistry {
    private final Map<String, ResearchAgentTool> tools;

    public ResearchAgentToolRegistry(List<ResearchAgentTool> tools) {
        Map<String, ResearchAgentTool> indexed = new LinkedHashMap<String, ResearchAgentTool>();
        if (tools != null) {
            for (ResearchAgentTool tool : tools) {
                if (tool == null || tool.descriptor() == null || blank(tool.descriptor().getCode())) {
                    throw new IllegalArgumentException("研究 Agent 工具必须提供编码");
                }
                String code = tool.descriptor().getCode();
                if (indexed.put(code, tool) != null) {
                    throw new IllegalArgumentException("研究 Agent 工具编码重复：" + code);
                }
            }
        }
        this.tools = Collections.unmodifiableMap(indexed);
    }

    public ResearchAgentTool required(String code) {
        ResearchAgentTool tool = tools.get(code);
        if (tool == null) {
            throw new IllegalArgumentException("研究 Agent 工具未注册：" + code);
        }
        return tool;
    }

    public List<ResearchToolDescriptor> list() {
        List<ResearchToolDescriptor> values = new ArrayList<ResearchToolDescriptor>();
        for (ResearchAgentTool tool : tools.values()) {
            values.add(tool.descriptor());
        }
        return Collections.unmodifiableList(values);
    }

    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
