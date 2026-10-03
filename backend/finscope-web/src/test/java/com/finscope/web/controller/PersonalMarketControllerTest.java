package com.finscope.web.controller;

import com.finscope.service.marketpulse.PersonalMarketService;
import com.finscope.domain.marketpulse.PersonalMarketChange;
import com.finscope.common.enums.marketpulse.PersonalChangeCategory;
import com.finscope.web.config.FinScopeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDate;
import java.util.List;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PersonalMarketController.class)
@Import(FinScopeProperties.class)
class PersonalMarketControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockBean
    private PersonalMarketService service;

    @Test
    void returnsTypedChangesAndRejectsInvalidDate() throws Exception {
        var item = new PersonalMarketChange();
        item.setId("event:1");
        item.setCategory(PersonalChangeCategory.COMPANY);
        item.setSampleId(1L);
        item.setCode("600519");
        when(service.changes(LocalDate.parse("2026-09-30"))).thenReturn(List.of(item));
        mvc.perform(get("/api/market-pulse/personal?businessDate=2026-09-30"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].category").value("COMPANY"))
                .andExpect(jsonPath("$.data[0].sampleId").value(1));
        mvc.perform(get("/api/market-pulse/personal?businessDate=bad"))
                .andExpect(status().isBadRequest());
    }
}
