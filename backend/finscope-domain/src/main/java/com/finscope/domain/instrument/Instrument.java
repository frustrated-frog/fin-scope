package com.finscope.domain.instrument;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标的实体：股票 / 基金 / 板块，作为标的视角的一等公民。
 */
@Data
public class Instrument {
    private Long id;
    /** 标的代码：600519 / 000001 / BK0477 */
    private String code;
    /** 标的类型：STOCK | FUND | SECTOR */
    private String type;
    private String name;
    /** 市场：SH | SZ（基金/板块可为空） */
    private String market;
    /** 别名，逗号分隔，用于新闻匹配，如 "茅台,飞天,600519" */
    private String aliases;
    /** 所属板块代码（个股用） */
    private String sectorCode;
    /** 产业链标签，逗号分隔（后期由 Agent 生成） */
    private String chainTags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}