package com.finscope.domain.insight;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InsightSection {
    /**
     * 标题。
     */
    private String title;
    /**
     * 正文内容。
     */
    private String content;
}
