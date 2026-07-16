package com.finscope.domain.marketintel;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 一次公开披露的龙虎榜上榜事件。
 */
@Data
public class DragonTigerRecord {
    private Long id;
    private Long instrumentId;
    private String providerCode;
    private LocalDate tradeDate;
    private String externalId;
    private String reasonCode;
    private String reason;
    private String providerExplanation;
    private BigDecimal closePrice;
    private BigDecimal changeRate;
    private BigDecimal buyAmount;
    private BigDecimal sellAmount;
    private BigDecimal netAmount;
    private BigDecimal billboardAmount;
    private BigDecimal marketAmount;
    private BigDecimal netAmountRatio;
    private BigDecimal billboardAmountRatio;
    private BigDecimal turnoverRate;
    private BigDecimal freeMarketCap;
    private List<DragonTigerSeat> seats = Collections.emptyList();
    private LocalDateTime retrievedAt;
    private String payloadHash;
    private String qualityStatus;

    public List<DragonTigerSeat> getSeats() {
        return seats;
    }

    public void setSeats(List<DragonTigerSeat> seats) {
        this.seats = Collections.unmodifiableList(new ArrayList<DragonTigerSeat>(
                seats == null ? Collections.<DragonTigerSeat>emptyList() : seats));
    }

    public List<DragonTigerSeat> getBuySeats() {
        return seats("BUY");
    }

    public List<DragonTigerSeat> getSellSeats() {
        return seats("SELL");
    }

    private List<DragonTigerSeat> seats(String direction) {
        return seats.stream()
                .filter(value -> direction.equals(value.getDirection()))
                .sorted(Comparator.comparing(DragonTigerSeat::getRank,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.collectingAndThen(
                        Collectors.toList(), Collections::unmodifiableList));
    }
}
