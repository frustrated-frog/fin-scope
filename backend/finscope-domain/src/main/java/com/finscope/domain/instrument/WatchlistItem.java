package com.finscope.domain.instrument;

import java.time.LocalDateTime;

/**
 * 自选面板条目：把一个标的加入用户关注列表。
 */
public class WatchlistItem {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 标的 ID。
     */
    private Long instrumentId;
    /**
     * 分组名称。
     */
    private String groupName;
    /**
     * 排序序号。
     */
    private int sortOrder;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    // 关联展示字段（来自 instrument join，非持久化列）
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
     * 所属板块编码。
     */
    private String sectorCode;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Long instrumentId) {
        this.instrumentId = instrumentId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
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

    public String getSectorCode() {
        return sectorCode;
    }

    public void setSectorCode(String sectorCode) {
        this.sectorCode = sectorCode;
    }
}