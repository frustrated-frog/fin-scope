package com.finscope.web.response;

import com.finscope.domain.marketpulse.MarketPulseBackfillResult;
import com.finscope.domain.marketpulse.MarketPulseRefreshResult;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 历史市场判断回填响应，日期统一输出 ISO 文本。 */
@Data
public class MarketPulseBackfillResponse {
    private String startDate;
    private String endDate;
    private String status;
    private List<DayResult> results = new ArrayList<>();
    private Map<String, String> failures = new LinkedHashMap<>();

    public static MarketPulseBackfillResponse of(MarketPulseBackfillResult source) {
        MarketPulseBackfillResponse value = new MarketPulseBackfillResponse();
        value.setStartDate(source.getStartDate() == null ? null : source.getStartDate().toString());
        value.setEndDate(source.getEndDate() == null ? null : source.getEndDate().toString());
        value.setStatus(source.getStatus());
        value.setFailures(new LinkedHashMap<>(source.getFailures()));
        for (MarketPulseRefreshResult result : source.getResults()) {
            value.getResults().add(DayResult.of(result));
        }
        return value;
    }

    @Data
    public static class DayResult {
        private String businessDate;
        private String status;
        private String qualityStatus;
        private int sectorCount;
        private int eventConfirmationCount;
        private int candidateCount;

        private static DayResult of(MarketPulseRefreshResult source) {
            DayResult value = new DayResult();
            value.setBusinessDate(source.getBusinessDate() == null
                    ? null : source.getBusinessDate().toString());
            value.setStatus(source.getStatus());
            value.setQualityStatus(source.getQualityStatus() == null
                    ? null : source.getQualityStatus().name());
            value.setSectorCount(source.getSectorCount());
            value.setEventConfirmationCount(source.getEventConfirmationCount());
            value.setCandidateCount(source.getCandidateCount());
            return value;
        }
    }
}
