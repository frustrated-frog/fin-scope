package com.finscope.service.quant.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.quant.PaperStockNameRepository;
import com.finscope.domain.quant.paper.ObservedIndustry;
import com.finscope.rpc.quant.PythonPaperClient;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PaperPortfolioRiskService {
    @Resource
    private PythonPaperClient paper;
    @Resource
    private PaperStockNameRepository stocks;

    public JsonNode analyze(long version, LocalDate day, int window) {
        if (version <= 0 || !List.of(20, 60, 120).contains(window)) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "请选择有效账户及 20、60 或 120 日窗口");
        }
        ObjectNode report = (ObjectNode) paper.portfolioRisk(version, day, window);
        ArrayNode industries = report.putArray("industries");
        List<String> codes = new ArrayList<>();
        for (JsonNode holding : report.path("holdings")) {
            codes.add(holding.path("code").asText());
        }
        if (codes.isEmpty()) {
            return report;
        }
        var cutoff = OffsetDateTime.parse(report.path("at").asText())
                .atZoneSameInstant(ZoneId.of("Asia/Shanghai")).toLocalDateTime();
        var memberships = stocks.industriesAt(codes, cutoff);
        var names = stocks.findNames(codes);
        Map<String, List<JsonNode>> groups = new LinkedHashMap<>();
        for (JsonNode node : report.path("holdings")) {
            ObjectNode row = (ObjectNode) node;
            String code = row.path("code").asText();
            row.put("name", names.get(code));
            ObservedIndustry membership = memberships.get(code);
            String sector = membership == null ? null : membership.name();
            row.put("industry", sector);
            if (membership != null) {
                row.put("industryAsOf", membership.businessDate().toString());
            }
            groups.computeIfAbsent(sector, key -> new ArrayList<>()).add(row);
        }
        List<ObjectNode> summaries = new ArrayList<>();
        for (var entry : groups.entrySet()) {
            ObjectNode summary = report.objectNode();
            summary.put("industry", entry.getKey());
            summary.put("count", entry.getValue().size());
            sumWhenComplete(summary, "weight", entry.getValue());
            sumWhenComplete(summary, "riskShare", entry.getValue());
            summaries.add(summary);
        }
        summaries.sort(Comparator.comparingDouble((ObjectNode row) -> row.path("weight").asDouble(0)).reversed());
        summaries.forEach(industries::add);
        summaries.stream().filter(s -> s.path("industry").isTextual() && s.path("weight").isNumber())
                .findFirst().ifPresent(s -> report.withArray("shocks").addObject()
                        .put("kind", "SECTOR").put("industry", s.path("industry").asText())
                        .put("shock", -0.1).put("change", -0.1 * s.path("weight").asDouble()));
        return report;
    }

    private void sumWhenComplete(ObjectNode summary, String field, List<JsonNode> rows) {
        double total = 0;
        for (JsonNode row : rows) {
            if (!row.path(field).isNumber()) {
                summary.putNull(field);
                return;
            }
            total += row.path(field).asDouble();
        }
        summary.put(field, total);
    }
}
