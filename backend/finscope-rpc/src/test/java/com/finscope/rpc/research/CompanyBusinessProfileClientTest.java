package com.finscope.rpc.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.instrument.Instrument;
import com.finscope.rpc.acquisition.AcquisitionResponse;
import com.finscope.rpc.acquisition.AcquisitionRuntime;
import com.finscope.rpc.marketintel.ProviderContractException;
import com.finscope.rpc.research.material.CompanyBusinessProfileClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CompanyBusinessProfileClientTest {
    @Test
    void mapsBusinessFieldsOnlyAfterCheckingSecurityIdentity() {
        var client = client("{\"jbzl\":{\"agdm\":\"603618\",\"gsmc\":\"杭州电缆股份有限公司\",\"gsjj\":\"公司简介\",\"jyfw\":\"经营范围\",\"sshy\":\"输配电气\"}}");
        var profile = client.fetch(stock());
        assertEquals(5L, profile.getInstrumentId());
        assertEquals("杭州电缆股份有限公司", profile.getCompanyName());
        assertEquals("经营范围", profile.getBusinessScope());
        assertTrue(profile.getSourceUrl().endsWith("SH603618"));
        assertNotNull(profile.getFetchedAt());
    }

    @Test
    void refusesDifferentSecurityMalformedJsonAndEmptyProviderResponse() {
        for (String body : new String[]{"{\"jbzl\":{\"agdm\":\"600519\",\"gsmc\":\"其他公司\"}}", "<html>error</html>", "{}"}) {
            assertThrows(ProviderContractException.class, () -> client(body).fetch(stock()));
        }
    }

    private CompanyBusinessProfileClient client(String body) {
        var client = new CompanyBusinessProfileClient();
        AcquisitionRuntime runtime = request -> {
            assertTrue(request.getUri().toString().endsWith("code=SH603618"));
            return new AcquisitionResponse(request.getUri(), request.getUri(), 200, Map.of(), new byte[0], body,
                    "application/json", "UTF-8", "hash", 1, 1, Instant.now());
        };
        ReflectionTestUtils.setField(client, "runtime", runtime);
        ReflectionTestUtils.setField(client, "json", new ObjectMapper());
        return client;
    }

    private Instrument stock() {
        var stock = new Instrument();
        stock.setId(5L);
        stock.setCode("603618");
        stock.setMarket("SH");
        return stock;
    }
}
