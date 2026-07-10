package com.finscope.domain.instrument;

import java.time.LocalDateTime;

/**
 * 标的实体：股票 / 基金 / 板块，作为标的视角的一等公民。
 */
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMarket() {
        return market;
    }

    public void setMarket(String market) {
        this.market = market;
    }

    public String getAliases() {
        return aliases;
    }

    public void setAliases(String aliases) {
        this.aliases = aliases;
    }

    public String getSectorCode() {
        return sectorCode;
    }

    public void setSectorCode(String sectorCode) {
        this.sectorCode = sectorCode;
    }

    public String getChainTags() {
        return chainTags;
    }

    public void setChainTags(String chainTags) {
        this.chainTags = chainTags;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}