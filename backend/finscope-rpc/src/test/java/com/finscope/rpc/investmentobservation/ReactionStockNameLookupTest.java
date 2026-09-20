package com.finscope.rpc.investmentobservation;

import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.URI;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReactionStockNameLookupTest {
    @Test
    void acceptsOnlyAStocksAndRejectsBrokenProviderContract() throws Exception {
        FinanceHttpClient http = mock(FinanceHttpClient.class);
        ReactionStockNameLookup lookup = new ReactionStockNameLookup();
        ReflectionTestUtils.setField(lookup, "http", http);
        when(http.get(anyString(), any(URI.class), anyMap(), anyInt(), anyInt())).thenReturn(
                new FinanceHttpResponse(200, "{\"QuotationCodeTable\":{\"Status\":0,\"Data\":["
                        + "{\"Code\":\"300476\",\"Name\":\"胜宏科技\",\"Classify\":\"AStock\"},"
                        + "{\"Code\":\"000001\",\"Name\":\"某指数\",\"Classify\":\"Index\"}]}}", Instant.now(), "hash"));
        var result = lookup.search("胜宏科技");
        assertEquals(1, result.size());
        assertEquals("300476", result.get(0).getCode());
        verify(http).get(eq("REACTION_STOCK_NAME"), argThat(uri -> uri.getRawQuery().contains("input=%")), anyMap(), eq(262144), eq(3000));
        when(http.get(anyString(), any(URI.class), anyMap(), anyInt(), anyInt()))
                .thenReturn(new FinanceHttpResponse(200, "{}", Instant.now(), "hash"));
        assertThrows(ProviderContractException.class, () -> lookup.search("胜宏科技"));
        assertTrue(lookup.search("").isEmpty());
    }
}
