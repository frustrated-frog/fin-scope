package com.finscope.rpc.research.material;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.fundamentals.CompanyBusinessProfile;
import com.finscope.domain.instrument.Instrument;
import com.finscope.rpc.acquisition.AcquisitionRequest;
import com.finscope.rpc.acquisition.AcquisitionRuntime;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class CompanyBusinessProfileClient {
    @Resource
    private AcquisitionRuntime runtime;
    @Resource
    private ObjectMapper json;

    public CompanyBusinessProfile fetch(Instrument instrument) {
        if (!instrument.getCode().matches("[0-9]{6}") || !java.util.Set.of("SH", "SZ", "BJ").contains(instrument.getMarket())) {
            throw new IllegalArgumentException("公司资料仅支持 A 股股票");
        }
        String url = "https://emweb.securities.eastmoney.com/PC_HSF10/CompanySurvey/CompanySurveyAjax?code="
                + instrument.getMarket() + instrument.getCode();
        var request = AcquisitionRequest.get(URI.create(url)).purpose("COMPANY_BUSINESS_PROFILE")
                .header("Referer", "https://emweb.securities.eastmoney.com/")
                .connectTimeoutMs(3000).readTimeoutMs(5000).deadlineMs(8000).maxRetries(0)
                .maxResponseBytes(2 * 1024 * 1024).build();
        var response = runtime.fetch(request);
        try {
            var source = json.readTree(response.getBodyText()).path("jbzl");
            if (!instrument.getCode().equals(source.path("agdm").asText()) || source.path("gsmc").asText("").isBlank()) {
                throw new ProviderContractException("INVALID_RESPONSE", "公司资料身份不匹配", false);
            }
            CompanyBusinessProfile result = new CompanyBusinessProfile();
            result.setInstrumentId(instrument.getId());
            result.setCompanyName(source.path("gsmc").asText());
            result.setIndustry(source.path("sshy").asText(""));
            result.setDescription(source.path("gsjj").asText("").strip());
            result.setBusinessScope(source.path("jyfw").asText("").strip());
            result.setSourceUrl("https://emweb.securities.eastmoney.com/PC_HSF10/CompanySurvey/Index?type=web&code="
                    + instrument.getMarket() + instrument.getCode());
            result.setFetchedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
            return result;
        } catch (JsonProcessingException error) {
            throw new ProviderContractException("INVALID_RESPONSE", "公司资料响应无法解析", false, error);
        }
    }
}
