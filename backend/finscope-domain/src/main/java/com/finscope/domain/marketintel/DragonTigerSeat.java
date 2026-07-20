package com.finscope.domain.marketintel;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 龙虎榜买入或卖出榜单中的公开席位事实。
 */
@Data
public class DragonTigerSeat {
    private Long id;
    private Long recordId;
    private String externalTradeId;
    private String seatCode;
    private String seatName;
    private String direction;
    private Integer rank;
    private BigDecimal buyAmount;
    private BigDecimal sellAmount;
    private BigDecimal netAmount;
    private BigDecimal buyRatio;
    private BigDecimal sellRatio;
    private String seatType;
    private boolean institutional;
    private boolean northbound;
    private LocalDateTime retrievedAt;
    private String payloadHash;
}
