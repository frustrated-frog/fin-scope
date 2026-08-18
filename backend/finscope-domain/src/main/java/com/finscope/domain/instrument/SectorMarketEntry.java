package com.finscope.domain.instrument;

import lombok.Data;

import java.time.LocalDateTime;

/** 同一批次板块目录快照中的一个板块行情。 */
@Data
public class SectorMarketEntry {
    /**
     * 业务编码。
     */
    private String code;
    /**
     * 名称。
     */
    private String name;
    /**
     * 内容分类。
     */
    private SectorCategory category;
    /**
     * 上游每日榜单原始名次；目录型条目为空。
     */
    private Integer sourceRank;
    /**
     * 主力净流入，单位为元；目录型条目为空。
     */
    private Double mainNetInflow;
    /**
     * 最新价格。
     */
    private Double price;
    /**
     * 涨跌额。
     */
    private Double changeAmount;
    /**
     * 涨跌幅百分比。
     */
    private Double changePct;
    /**
     * 换手率或成交额。
     */
    private Double turnover;
    /**
     * 领涨股票代码。
     */
    private String leaderStockCode;
    /**
     * 领涨股票名称。
     */
    private String leaderStockName;
    /**
     * 领涨股票涨跌幅百分比。
     */
    private Double leaderStockChangePct;
    /**
     * 行情时间。
     */
    private LocalDateTime quoteTime;
}
