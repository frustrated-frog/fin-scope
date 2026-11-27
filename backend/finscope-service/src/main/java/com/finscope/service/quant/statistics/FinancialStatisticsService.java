package com.finscope.service.quant.statistics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finscope.common.enums.marketpulse.MarketStage;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.marketpulse.MarketPulseRepository;
import com.finscope.domain.quant.statistics.FinancialStatisticsInput;
import com.finscope.rpc.quant.PythonPaperClient;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.DoubleSummaryStatistics;
import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class FinancialStatisticsService {
    private static final Pattern SYMBOL = Pattern.compile("(SH|SZ):[0-9]{6}");
    @Resource
    private PythonPaperClient paper;
    @Resource
    private MarketPulseRepository pulse;
    @Resource
    private ObjectMapper json;

    public ObjectNode analyze(FinancialStatisticsInput input) {
        if (input.asset() == null || input.peer() == null || !SYMBOL.matcher(input.asset()).matches()
                || !SYMBOL.matcher(input.peer()).matches() || input.asset().equals(input.peer())) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "请选择两种不同的沪深标的");
        }
        if (input.asOf() == null || input.asOf().isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai")))
                || input.days() < 30 || input.days() > 1095 || input.window() < 5 || input.window() > 120
                || !Double.isFinite(input.annualRiskFree()) || input.annualRiskFree() < 0 || input.annualRiskFree() > .25) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "请检查截止日、研究区间、滚动窗口和无风险利率");
        }
        ObjectNode request = json.valueToTree(input);
        request.put("asOf", input.asOf().toString());
        ObjectNode result = (ObjectNode) paper.financialStatistics(request);
        JsonNode days = result.required("daily");
        Map<LocalDate, MarketStage> stages = pulse.timelyStages(LocalDate.parse(days.get(0).required("prior").asText()), input.asOf());
        Map<MarketStage, DoubleSummaryStatistics> groups = new EnumMap<>(MarketStage.class);
        for (JsonNode day : days) {
            MarketStage stage = stages.get(LocalDate.parse(day.required("prior").asText()));
            if (stage != null) {
                groups.computeIfAbsent(stage, ignored -> new DoubleSummaryStatistics()).accept(day.required("value").doubleValue());
            }
        }
        var rows = result.putArray("marketStages");
        groups.forEach((stage, values) -> rows.addObject().put("stage", stage.name())
                .put("n", values.getCount()).put("mean", values.getAverage()));
        result.remove("daily");
        return result;
    }
}
