package com.finscope.web.request.quant;

public class CreateQuantDatasetRequest {
    private String name; private String dataKind = "REAL";
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDataKind() { return dataKind; }
    public void setDataKind(String dataKind) { this.dataKind = dataKind; }
}
