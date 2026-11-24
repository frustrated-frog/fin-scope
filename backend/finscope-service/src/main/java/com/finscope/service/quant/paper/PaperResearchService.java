package com.finscope.service.quant.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finscope.common.enums.quant.PaperUniverse;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.instrument.WatchlistRepository;
import com.finscope.dao.quant.QuantStrategyRepository;
import com.finscope.domain.quant.discovery.StockDiscoveryRun;
import com.finscope.domain.quant.paper.PaperResearchInput;
import com.finscope.domain.quant.strategy.QuantStrategyVersion;
import com.finscope.rpc.llm.LlmChatClient;
import com.finscope.rpc.quant.PythonPaperClient;
import com.finscope.service.quant.discovery.StockDiscoveryService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class PaperResearchService {
    @Resource
    private WatchlistRepository watchlist;
    @Resource
    private StockDiscoveryService discoveries;
    @Resource
    private QuantStrategyRepository strategies;
    @Resource
    private PythonPaperClient paper;
    @Resource
    private LlmChatClient llm;
    @Resource
    private ObjectMapper json;

    public JsonNode versions() {
        return paper.versions();
    }

    public JsonNode detail(long id, LocalDate day) {
        return paper.detail(id, day);
    }

    public JsonNode deploy(long id) {
        return paper.deploy(id);
    }

    public JsonNode generate(PaperResearchInput input) {
        if (input.hypothesis() == null || input.hypothesis().isBlank() || input.hypothesis().length() > 1200) {
            throw invalid("请填写 1 至 1200 字研究假设");
        }
        ObjectNode spec = json.createObjectNode();
        JsonNode parent = null;
        if (input.parent() != null) {
            parent = paper.detail(input.parent(), null).path("spec");
            spec.setAll((ObjectNode) parent);
            spec.put("parent", input.parent());
        } else {
            freezePool(input, spec);
        }
        String origin = input.hypothesis();
        if (input.academyVersionId() != null) {
            QuantStrategyVersion version = strategies.findVersion(input.academyVersionId()).orElseThrow(() -> invalid("学院策略版本不存在"));
            origin = "学院策略 #" + version.getId() + " 的盘中衍生实验 · " + origin;
            spec.put("origin", origin);
            origin += "\n原策略定义：" + version.getSpecJson();
        } else {
            if (input.factorCode() != null && input.factorCode().length() > 100) {
                throw invalid("因子标识过长");
            }
            if (input.factorCode() != null) {
                origin = "因子 " + input.factorCode() + " 的盘中研究 · " + origin;
            }
            spec.put("origin", origin);
        }
        ObjectNode rule = generateRule(origin, parent);
        if (parent != null) {
            for (String field : List.of("entry", "exit", "slots")) {
                rule.set(field, parent.path("rule").get(field));
            }
            if (rule.path("terms").equals(parent.path("rule").path("terms"))) {
                throw invalid("模型未改变因子公式，请具体说明要检验的变化");
            }
        }
        spec.set("rule", rule);
        return paper.research(spec);
    }

    private ObjectNode generateRule(String origin, JsonNode parent) {
        if (!llm.isConfigured()) {
            throw invalid("请先配置本地 LLM，再生成盘中策略");
        }
        String contract = "生成可执行的沪深 A 股五分钟实验规则，仅返回 JSON 对象。"
                + "字段严格为 name,hypothesis,terms,entry,exit,slots。中文写明可检验假设。"
                + "terms 为 1-4 项的数组，每项仅有 feature,window(2-48根),weight(-10到10)。"
                + "可用特征 momentum=窗口涨跌幅、volatility=窗口收益标准差、volumeSurprise=当前量/前窗均量-1、"
                + "closeLocation=收盘在窗口高低区间的相对位置减0.5。得分为加权和。"
                + "entry 和 exit 取值 -20至20，entry>exit，使用小数尺度，slots 为1到10的持股数。"
                + "排名靠前且达entry买入，持仓低于或等于exit退出。如源策略需要无法获得的数据，说明代理指标与局限。";
        try {
            String raw = llm.complete(contract, origin + (parent == null ? "" : "\n对照版本，仅改变因子组合：" + parent.path("rule")));
            return json.readValue(raw.trim().replaceFirst("^\u0060\u0060\u0060(?:json)?\\s*", "").replaceFirst("\\s*\u0060\u0060\u0060$", ""), ObjectNode.class);
        } catch (Exception error) {
            throw new BusinessException(ErrorCode.LLM_SERVICE_ERROR, "未取得有效的策略 JSON，请重试", error);
        }
    }

    private void freezePool(PaperResearchInput input, ObjectNode spec) {
        if (input.pool() == null || input.capital() == null || input.capital().signum() <= 0
                || input.capital().scale() > 2 || input.capital().compareTo(new BigDecimal("10000000000")) > 0) {
            throw invalid("请选择股票池并填写有效本金，最多两位小数");
        }
        Set<String> codes = new LinkedHashSet<>();
        if (input.pool() == PaperUniverse.WATCHLIST) {
            watchlist.findByTypes(List.of("STOCK")).forEach(item -> addCode(codes, item.getCode()));
            spec.put("source", "自选股 · 创建时快照");
        } else {
            StockDiscoveryRun run = discoveries.latest().orElseThrow(() -> invalid("暂无已完成的股票发现报告"));
            if (run.getReportJson() == null || run.getCompletedAt() == null) {
                throw invalid("股票发现报告尚未完成");
            }
            try {
                JsonNode candidates = json.readTree(run.getReportJson()).path("candidates");
                for (JsonNode candidate : candidates) {
                    addCode(codes, candidate.path("code").asText());
                }
            } catch (Exception error) {
                throw invalid("候选池报告无法解析");
            }
            spec.put("source", "股票发现 #" + run.getId() + " · " + run.getCompletedAt());
        }
        if (codes.isEmpty()) {
            throw invalid("所选股票池中暂无支持的沪深 A 股");
        }
        spec.put("pool", input.pool().name());
        spec.put("capital", input.capital());
        spec.set("codes", json.valueToTree(codes.stream().limit(24).toList()));
    }

    private void addCode(Set<String> codes, String code) {
        if (code != null && code.matches("(00|30|60|68)[0-9]{4}")) {
            codes.add(code);
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, message);
    }
}
