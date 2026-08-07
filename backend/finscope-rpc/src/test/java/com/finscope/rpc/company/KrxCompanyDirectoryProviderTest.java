package com.finscope.rpc.company;

import com.finscope.domain.company.CompanySearchResult;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KrxCompanyDirectoryProviderTest {
    @Test
    void searchesKoreanCompaniesByChineseAliasAndDistinguishesSkEntities() {
        String html = "<table><tr><th>회사명</th><th>시장구분</th><th>종목코드</th><th>업종</th></tr>"
                + "<tr><td>SK증권제13호스팩</td><td>코스닥</td><td>473950</td><td>금융 지원 서비스업</td></tr>"
                + "<tr><td>SK</td><td>유가</td><td>034730</td><td>기타 금융업</td></tr>"
                + "<tr><td>SK하이닉스</td><td>유가</td><td>000660</td><td>반도체 제조업</td></tr></table>";
        FinanceHttpClient http = (provider, uri, headers) -> new FinanceHttpResponse(
                200, html, Instant.parse("2026-08-08T00:00:00Z"), "hash");

        List<CompanySearchResult> results = new KrxCompanyDirectoryProvider(http).search("海力士", 10);

        assertEquals(1, results.size());
        assertEquals("SK hynix Inc.", results.get(0).getDisplayName());
        assertEquals("SK하이닉스", results.get(0).getNativeName());
        assertEquals("000660", results.get(0).getSecurities().get(0).getSymbol());
        assertEquals("KRX", results.get(0).getSecurities().get(0).getExchange());
        assertEquals("KR", results.get(0).getCountryCode());
    }

    @Test
    void ranksExactGroupAndOperatingCompanyBeforeIncidentalSkMatches() {
        String html = "<table>"
                + "<tr><td>SK증권제13호스팩</td><td>코스닥</td><td>473950</td><td>금융</td></tr>"
                + "<tr><td>SK하이닉스</td><td>유가</td><td>000660</td><td>반도체</td></tr>"
                + "<tr><td>SK</td><td>유가</td><td>034730</td><td>지주회사</td></tr></table>";
        FinanceHttpClient http = (provider, uri, headers) -> new FinanceHttpResponse(
                200, html, Instant.parse("2026-08-08T00:00:00Z"), "hash");

        List<CompanySearchResult> results = new KrxCompanyDirectoryProvider(http).search("SK", 2);

        assertEquals("SK Inc.", results.get(0).getDisplayName());
        assertEquals("SK hynix Inc.", results.get(1).getDisplayName());
    }
}
