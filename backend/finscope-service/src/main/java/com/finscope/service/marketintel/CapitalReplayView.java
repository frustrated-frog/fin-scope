package com.finscope.service.marketintel;

import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Value
public class CapitalReplayView {
    List<Session> sessions;
    List<String> warnings;

    @Value
    public static class Session {
        LocalDate date;
        String priceProvider;
        String flowProvider;
        List<Minute> minutes;
    }

    @Value
    public static class Minute {
        LocalTime time;
        BigDecimal price;
        BigDecimal tradeAmount;
        BigDecimal largeNet;
        BigDecimal superLargeNet;
    }
}
