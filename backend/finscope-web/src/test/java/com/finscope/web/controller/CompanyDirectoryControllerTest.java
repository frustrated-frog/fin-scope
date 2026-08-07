package com.finscope.web.controller;

import com.finscope.domain.company.CompanySearchResult;
import com.finscope.service.company.GlobalCompanySearchService;
import com.finscope.web.config.CorsConfig;
import com.finscope.web.config.FinScopeProperties;
import com.finscope.web.handler.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompanyDirectoryController.class)
@Import({ApiExceptionHandler.class, FinScopeProperties.class, CorsConfig.class})
class CompanyDirectoryControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private GlobalCompanySearchService search;

    @Test
    void searchesGlobalCompaniesByName() throws Exception {
        CompanySearchResult apple = new CompanySearchResult();
        apple.setProviderCode("SEC_EDGAR");
        apple.setProviderCompanyId("CIK0000320193");
        apple.setDisplayName("Apple Inc.");
        apple.setCountryCode("US");
        apple.setCapabilityLevel("L4");
        when(search.search("Apple", 8)).thenReturn(Collections.singletonList(apple));

        mockMvc.perform(get("/api/companies/search").param("q", "Apple").param("limit", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].displayName").value("Apple Inc."))
                .andExpect(jsonPath("$.data[0].capabilityLevel").value("L4"));
    }

    @Test
    void rejectsBlankQueriesInsteadOfCallingEveryProvider() throws Exception {
        mockMvc.perform(get("/api/companies/search").param("q", " "))
                .andExpect(status().isBadRequest());
    }
}
