package com.finscope.domain.fundamentals;

import lombok.Data;

@Data
public class FundamentalBusinessSource {
    private String evidenceRef;
    private String title;
    private String url;
    private String publishedAt;
    private String contentOrigin;
}
