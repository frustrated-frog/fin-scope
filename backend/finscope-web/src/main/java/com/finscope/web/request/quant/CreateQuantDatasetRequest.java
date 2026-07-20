package com.finscope.web.request.quant;

import lombok.Data;

@Data
public class CreateQuantDatasetRequest {
    private String name;
    private String dataKind = "REAL";
}
