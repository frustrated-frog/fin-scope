package com.finscope.rpc.quant.catalog;

import com.finscope.domain.quant.catalog.QuantStrategyCatalogSnapshot;

public interface QuantStrategyCatalogProvider {
    QuantStrategyCatalogSnapshot fetch();
}
