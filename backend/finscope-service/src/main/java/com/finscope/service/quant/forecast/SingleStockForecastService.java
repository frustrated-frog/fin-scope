package com.finscope.service.quant.forecast;

import com.finscope.domain.quant.forecast.SingleStockForecast;
import com.finscope.rpc.quant.PythonSingleStockForecastClient;
import org.springframework.stereotype.Service;

/** Keeps the web contract stable while Python owns all quantitative analysis. */
@Service
public class SingleStockForecastService {
    private final PythonSingleStockForecastClient client;

    public SingleStockForecastService(PythonSingleStockForecastClient client) {
        this.client = client;
    }

    public SingleStockForecast forecast(String requestedCode) {
        String normalized = requestedCode == null ? "" : requestedCode.trim().toUpperCase();
        if (normalized.matches("\\d{6}\\.(SH|SZ|BJ)")) {
            normalized = normalized.substring(0, 6);
        }
        if (!normalized.matches("\\d{6}")) {
            throw new IllegalArgumentException("股票代码必须是六位 A 股代码");
        }
        return client.forecast(normalized);
    }
}
