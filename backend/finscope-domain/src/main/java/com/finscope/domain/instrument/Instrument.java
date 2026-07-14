package com.finscope.domain.instrument;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标的实体：股票 / 基金 / 板块，作为标的视角的一等公民。
 */
@Data
public class Instrument {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 业务编码。
     */
    private String code;
    /**
     * 类型。
     */
    private String type;
    /**
     * 名称。
     */
    private String name;
    /**
     * 交易市场。
     */
    private String market;
    /**
     * 标的别名。
     */
    private String aliases;
    /**
     * 所属板块编码。
     */
    private String sectorCode;
    /**
     * 产业链标签。
     */
    private String chainTags;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 最近更新时间。
     */
    private LocalDateTime updatedAt;
}