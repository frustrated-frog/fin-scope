package com.finscope.domain.quant.discovery;

public interface StockDiscoveryEventPublisher {
    boolean publish(StockDiscoveryRequestedEvent event);
}
