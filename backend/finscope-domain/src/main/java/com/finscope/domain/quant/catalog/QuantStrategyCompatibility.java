package com.finscope.domain.quant.catalog;

import java.util.ArrayList;
import java.util.List;

public class QuantStrategyCompatibility {
    private String status;
    private List<String> mappedFactors = new ArrayList<String>();
    private List<String> missingFactors = new ArrayList<String>();
    private String adaptationNote;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<String> getMappedFactors() { return mappedFactors; }
    public void setMappedFactors(List<String> mappedFactors) { this.mappedFactors = mappedFactors; }
    public List<String> getMissingFactors() { return missingFactors; }
    public void setMissingFactors(List<String> missingFactors) { this.missingFactors = missingFactors; }
    public String getAdaptationNote() { return adaptationNote; }
    public void setAdaptationNote(String adaptationNote) { this.adaptationNote = adaptationNote; }
}
