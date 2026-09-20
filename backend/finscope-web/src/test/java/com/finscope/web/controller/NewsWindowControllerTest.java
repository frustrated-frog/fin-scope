package com.finscope.web.controller;

import com.finscope.domain.news.*;
import com.finscope.service.news.NewsWindowService;
import com.finscope.web.config.FinScopeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NewsWindowController.class)
@Import(FinScopeProperties.class)
class NewsWindowControllerTest {
    @Autowired private MockMvc mvc;
    @MockBean private NewsWindowService news;

    @Test
    void bindsTheFullQueryAndSerializesContentVersionAndReadState() throws Exception {
        NewsReport report = new NewsReport();
        report.setId("CLS:251");
        report.setContentVersion(2);
        report.setReadVersion(1);
        NewsWindowPage page = new NewsWindowPage();
        page.setItems(List.of(report));
        page.setTotal(251);
        when(news.query(any())).thenReturn(page);
        mvc.perform(get("/api/news/window").param("query", "公司").param("exclude", "减持")
                        .param("page", "2").param("size", "50").param("asOfSequence", "251").param("unreadOnly", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(251))
                .andExpect(jsonPath("$.data.items[0].id").value("CLS:251"))
                .andExpect(jsonPath("$.data.items[0].unread").value(true));
        verify(news).query(argThat(query -> query.getPage() == 2 && query.isUnreadOnly()
                && "公司".equals(query.getQuery()) && "减持".equals(query.getExclude()) && query.getAsOfSequence() == 251));
    }

    @Test
    void readAcknowledgesOnlyTheSubmittedVersion() throws Exception {
        mvc.perform(post("/api/news/window/read").param("id", "CLS:251").param("version", "2"))
                .andExpect(status().isOk());
        verify(news).read("CLS:251", 2);
    }
}
