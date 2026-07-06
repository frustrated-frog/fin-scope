package com.finscope.domain.request;

import lombok.Data;

@Data
public class IngestUrlRequest {
    private String url;
    private String sourceName;
    private String tags;
    private String category;
}
