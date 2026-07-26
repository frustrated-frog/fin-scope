package com.finscope.domain.research.mission;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ResearchToolDescriptor {
    private String code;
    private String name;
    private String description;
    private Map<String, String> inputSchema = Collections.emptyMap();
    private Map<String, String> outputSchema = Collections.emptyMap();
    private int timeoutMs;
    private boolean readOnly;
    private boolean parallelizable;
    private String riskLevel;
    private String budgetType;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, String> getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(Map<String, String> inputSchema) {
        this.inputSchema = immutable(inputSchema);
    }

    public Map<String, String> getOutputSchema() {
        return outputSchema;
    }

    public void setOutputSchema(Map<String, String> outputSchema) {
        this.outputSchema = immutable(outputSchema);
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public boolean isParallelizable() {
        return parallelizable;
    }

    public void setParallelizable(boolean parallelizable) {
        this.parallelizable = parallelizable;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getBudgetType() {
        return budgetType;
    }

    public void setBudgetType(String budgetType) {
        this.budgetType = budgetType;
    }

    private static Map<String, String> immutable(Map<String, String> value) {
        return value == null || value.isEmpty()
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, String>(value));
    }
}
