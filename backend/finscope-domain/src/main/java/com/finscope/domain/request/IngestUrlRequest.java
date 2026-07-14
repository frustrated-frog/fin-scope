package com.finscope.domain.request;

import lombok.Data;

@Data
public class IngestUrlRequest {
    /**
     * 资源 URL。
     */
    private String url;
    /**
     * 信息源名称。
     */
    private String sourceName;
    /**
     * 标签集合或标签字符串。
     */
    private String tags;
    /**
     * 内容分类。
     */
    private String category;
}
