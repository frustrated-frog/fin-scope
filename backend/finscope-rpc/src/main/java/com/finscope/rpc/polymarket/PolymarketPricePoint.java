package com.finscope.rpc.polymarket;

import lombok.Data;

@Data
public class PolymarketPricePoint {
    private long timestamp;
    private double price;
}
