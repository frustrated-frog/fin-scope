package com.finscope.service.quant.forecast;

import com.finscope.domain.quant.forecast.SingleStockForecast;
import com.finscope.rpc.quant.PythonSingleStockForecastClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SingleStockForecastServiceTest {
    @Test
    void delegatesQuantitativeWorkToPython() {
        PythonSingleStockForecastClient client = mock(PythonSingleStockForecastClient.class);
        SingleStockForecast expected = new SingleStockForecast();
        when(client.forecast("600519")).thenReturn(expected);

        SingleStockForecast result = new SingleStockForecastService(client).forecast("600519.SH");

        assertSame(expected, result);
        verify(client).forecast("600519");
    }
}
