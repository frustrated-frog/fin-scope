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
    void acceptsVerifiedBeijingListedSharesWithoutAdmittingOtherNeeqSecurities() throws Exception {
        FinanceHttpClient http = mock(FinanceHttpClient.class);
        var lookup = new ReactionStockNameLookup();
        ReflectionTestUtils.setField(lookup, "http", http);
        when(http.get(anyString(), any(URI.class), anyMap(), anyInt(), anyInt())).thenReturn(new FinanceHttpResponse(200, """
                {"QuotationCodeTable":{"Status":0,"Data":[
                    {"Code":"920190","Name":"雷神科技","Classify":"NEEQ","SecurityTypeName":"京A"},
                    {"Code":"830001","Name":"同名挂牌企业","Classify":"NEEQ","SecurityTypeName":"新三板"}]}}
                """, Instant.now(), "hash"));
        var result = lookup.search("雷神科技");
        assertEquals(1, result.size());
        assertEquals("920190", result.getFirst().getCode());
    }

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
        assertEquals(result, lookup.search("胜宏科技"));
        when(http.get(anyString(), any(URI.class), anyMap(), anyInt(), anyInt()))
                .thenReturn(new FinanceHttpResponse(200, "{}", Instant.now(), "hash"));
        assertThrows(ProviderContractException.class, () -> lookup.search("另一公司"));
        assertTrue(lookup.search("").isEmpty());
    }
}
